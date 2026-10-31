package com.mydiary.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mydiary.app.databinding.ActivityMainBinding
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var db: DiaryDbHelper
    private lateinit var adapter: DiaryAdapter
    private val allDiaries = ArrayList<Diary>()

    private var showCalendar = false

    // 当前日历展示的年月
    private var calYear = 0
    private var calMonth = 0   // 0-based

    private val weekNames = arrayOf("一", "二", "三", "四", "五", "六", "日")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        db = DiaryDbHelper(this)

        b.tvHeaderSub.text = DateUtil.prettyFull(DateUtil.today())

        adapter = DiaryAdapter { d -> openEditor(d.date) }
        b.rvList.layoutManager = LinearLayoutManager(this)
        b.rvList.adapter = adapter

        setupTabs()
        setupSearch()
        setupCalendarControls()
        setupMore()
        registerRestoreLauncher()

        val now = Calendar.getInstance()
        calYear = now.get(Calendar.YEAR)
        calMonth = now.get(Calendar.MONTH)

        b.tabList.performClick()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setupTabs() {
        b.tabList.setOnClickListener {
            showCalendar = false
            b.rvList.visibility = View.VISIBLE
            b.calendarBox.visibility = View.GONE
            setTabActive(list = true, calendar = false)
            refresh()
        }
        b.tabCalendar.setOnClickListener {
            showCalendar = true
            b.rvList.visibility = View.GONE
            b.calendarBox.visibility = View.VISIBLE
            setTabActive(list = false, calendar = true)
            renderCalendar()
        }
        b.tabNew.setOnClickListener {
            openEditor(DateUtil.today())
        }
    }

    private fun setTabActive(list: Boolean, calendar: Boolean) {
        val on = getColor(R.color.accent)
        val off = getColor(R.color.ink_faint)
        b.tabListIcon.setTextColor(if (list) on else off)
        b.tabListLabel.setTextColor(if (list) on else off)
        b.tabCalendarIcon.setTextColor(if (calendar) on else off)
        b.tabCalendarLabel.setTextColor(if (calendar) on else off)
    }

    private fun setupSearch() {
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val kw = s?.toString().orEmpty()
                // 一输入搜索词就切回列表视图
                if (kw.isNotEmpty() && showCalendar) {
                    b.tabList.performClick()
                    return   // performClick 内部会带着新关键词刷新
                }
                applyFilter(kw)
            }
        })
    }

    private fun setupCalendarControls() {
        b.btnPrevMonth.setOnClickListener {
            calMonth--
            if (calMonth < 0) { calMonth = 11; calYear-- }
            renderCalendar()
        }
        b.btnNextMonth.setOnClickListener {
            calMonth++
            if (calMonth > 11) { calMonth = 0; calYear++ }
            renderCalendar()
        }

        // 固定列宽，保证 7 列整齐。左右内边距 18dp、每格左右外边距各 1dp，
        // 与 renderCalendar() 里的 cellW 算法保持一致，表头才能和日期格子对齐。
        b.weekHeader.removeAllViews()
        val headerW = (resources.displayMetrics.widthPixels - dp(36) - dp(14)) / 7
        for (n in weekNames) {
            val tv = TextView(this)
            tv.text = n
            tv.gravity = Gravity.CENTER
            tv.setTextColor(getColor(R.color.ink_faint))
            tv.textSize = 12f
            val lp = LinearLayout.LayoutParams(headerW + dp(2), ViewGroup.LayoutParams.WRAP_CONTENT)
            tv.layoutParams = lp
            (tv.layoutParams as LinearLayout.LayoutParams).setMargins(dp(1), 0, dp(1), 0)
            b.weekHeader.addView(tv)
        }
    }

    private fun setupMore() {
        b.btnMore.setOnClickListener {
            val options = arrayOf("导出 Markdown", "备份数据到文件", "从备份恢复", "统计", "检查更新")
            MaterialAlertDialogBuilder(this)
                .setTitle("更多")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> ExportUtil.exportMarkdownAndShare(this, db)
                        1 -> ExportUtil.backupAndShare(this, db)
                        2 -> pickBackupFile()
                        3 -> showStats()
                        4 -> checkUpdate(manual = true)
                    }
                }
                .show()
        }
    }

    private fun checkUpdate(manual: Boolean) {
        if (manual) toast("正在检查…")
        UpdateChecker.check { info ->
            if (info == null) {
                if (manual) toast("已是最新版本（v${com.mydiary.app.BuildConfig.VERSION_NAME}）")
                return@check
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("发现新版本 v${info.version}")
                .setMessage(
                    buildString {
                        append("当前版本 v${info.currentVersion}\n\n")
                        if (info.notes.isNotBlank()) append(info.notes.take(500))
                    }
                )
                .setNegativeButton("以后再说", null)
                .setPositiveButton("去下载") { _, _ ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.pageUrl)))
                    }
                }
                .show()
        }
    }

    /** 让用户挑一个之前备份的 JSON 文件，确认后导入 */
    private fun pickBackupFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        restoreLauncher.launch(i)
    }

    // 必须在 Activity 创建完成后再注册，放属性初始化里在某些机型上会崩
    private lateinit var restoreLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    private fun registerRestoreLauncher() {
        restoreLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (json.isNullOrBlank()) {
                    toast("文件是空的")
                    return@registerForActivityResult
                }
                val (added, replaced) = db.restoreFromJson(json)
                refresh()
                MaterialAlertDialogBuilder(this)
                    .setTitle("恢复完成")
                    .setMessage("新增 $added 篇，覆盖 $replaced 篇。")
                    .setPositiveButton("好", null)
                    .show()
            } catch (e: Exception) {
                toast("恢复失败：${e.message}")
            }
        }
    }

    private fun refresh() {
        allDiaries.clear()
        allDiaries.addAll(db.listAll())
        applyFilter(b.etSearch.text?.toString().orEmpty())
        updateHeaderStats()
        if (showCalendar) renderCalendar()
    }

    /** 顶部摘要：本月写了几天 + 总计，比单纯的"还有多少篇"更有反馈感 */
    private fun updateHeaderStats() {
        val monthPrefix = DateUtil.today().substring(0, 7)   // yyyy-MM
        val thisMonth = allDiaries.count { it.date.startsWith(monthPrefix) }
        val total = allDiaries.size
        b.tvHeaderSub.text = when {
            total == 0 -> DateUtil.prettyFull(DateUtil.today())
            else -> "本月已写 $thisMonth 天 · 共 $total 篇"
        }
    }

    private fun applyFilter(keyword: String) {
        val filtered = allDiaries.filter { it.matches(keyword) }
        adapter.submit(filtered)

        val empty = filtered.isEmpty()
        b.rvList.visibility = if (showCalendar) View.GONE else if (empty) View.GONE else View.VISIBLE
        b.tvEmpty.visibility = if (!showCalendar && empty) View.VISIBLE else View.GONE
        b.tvEmpty.text = if (allDiaries.isEmpty()) getString(R.string.no_diary)
        else getString(R.string.no_result)
    }

    // ---------------- 日历 ----------------

    private fun renderCalendar() {
        val monthLabel = String.format(Locale.CHINA, "%d年%d月", calYear, calMonth + 1)
        b.tvMonth.text = monthLabel

        val prefix = String.format(Locale.CHINA, "%04d-%02d", calYear, calMonth + 1)
        val marked = db.datesInMonth(prefix)

        b.gridDays.removeAllViews()
        // 左右各 18dp 内边距，每个格子左右各 1dp 外边距：可用宽度要一并扣掉，
        // 否则 7 列会比屏幕宽出 14dp，窄屏上右侧会被裁掉。
        val cellW = (resources.displayMetrics.widthPixels - dp(36) - dp(14)) / 7

        // 当月 1 号是周几（周一为 0）
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, calYear)
            set(Calendar.MONTH, calMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val firstDow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 前导空格
        for (i in 0 until firstDow) {
            val v = View(this)
            b.gridDays.addView(v, cellParams(cellW))
        }

        val today = DateUtil.today()
        for (day in 1..daysInMonth) {
            val dateStr = String.format(Locale.CHINA, "%04d-%02d-%02d", calYear, calMonth + 1, day)
            val tv = TextView(this)
            tv.text = day.toString()
            tv.gravity = Gravity.CENTER
            tv.textSize = 14f

            val has = marked.contains(dateStr)
            if (dateStr == today) {
                tv.setBackgroundResource(R.drawable.bg_cal_today)
                tv.setTextColor(getColor(R.color.white))
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
            } else if (has) {
                tv.setBackgroundResource(R.drawable.bg_cal_has)
                tv.setTextColor(getColor(R.color.ink))
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tv.setTextColor(getColor(R.color.ink_soft))
            }

            tv.setOnClickListener { openEditor(dateStr) }
            b.gridDays.addView(tv, cellParams(cellW))
        }
    }

    private fun cellParams(w: Int): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = w
            height = dp(44)
            val margin = dp(1)
            setMargins(margin, margin, margin, margin)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    // ---------------- 其它 ----------------

    private fun openEditor(date: String) {
        val i = Intent(this, EditActivity::class.java).putExtra(EditActivity.EXTRA_DATE, date)
        startActivity(i)
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()

    private fun showStats() {        val total = db.count()
        val moods = db.moodCounts()
        val tags = db.tagCounts().entries.sortedByDescending { it.value }.take(10)

        val sb = StringBuilder()
        sb.append("共记录 ").append(total).append(" 天\n")
        if (moods.isNotEmpty()) {
            sb.append("\n心情分布：\n")
            moods.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                sb.append("  ").append(DiaryDbHelper.moodEmoji(k))
                    .append(" ").append(DiaryDbHelper.moodLabel(k))
                    .append("  ").append(v).append(" 次\n")
            }
        }
        if (tags.isNotEmpty()) {
            sb.append("\n常用标签：\n  ")
            sb.append(tags.joinToString("、") { "${it.key}(${it.value})" })
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("我的记录")
            .setMessage(sb.toString())
            .setPositiveButton("好", null)
            .show()
    }
}
