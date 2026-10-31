package com.mydiary.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mydiary.app.databinding.ActivityEditBinding

class EditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATE = "extra_date"
    }

    private lateinit var b: ActivityEditBinding
    private lateinit var db: DiaryDbHelper

    private var date: String = ""
    private var selectedMood: String = ""
    private var selectedWeather: String = ""
    private var existing: Diary? = null
    private var dirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditBinding.inflate(layoutInflater)
        setContentView(b.root)

        db = DiaryDbHelper(this)
        date = intent.getStringExtra(EXTRA_DATE) ?: DateUtil.today()

        b.tvDate.text = DateUtil.prettyFull(date)
        if (date == DateUtil.today()) {
            b.tvDate.text = "今天 · " + DateUtil.prettyFull(date)
        }

        existing = db.findByDate(date)
        existing?.let {
            b.etTitle.setText(it.title)
            b.etContent.setText(it.content)
            b.etTags.setText(it.tags)
            selectedMood = it.mood
            selectedWeather = it.weather
            b.btnDelete.visibility = android.view.View.VISIBLE
        }

        // 上次没保存完的草稿：只在它比已存内容更新时才提示恢复
        val draft = db.findDraft(date)
        if (draft != null && draft.content.isNotBlank()) {
            val isNewer = existing == null || draft.updatedAt > existing!!.updatedAt
            val differs = existing == null ||
                    draft.title != existing!!.title || draft.content != existing!!.content
            if (isNewer && differs) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("发现未保存的草稿")
                    .setMessage("上次写到一半没有保存，要恢复吗？")
                    .setNegativeButton("不用了") { _, _ -> db.clearDraft(date) }
                    .setPositiveButton("恢复") { _, _ ->
                        b.etTitle.setText(draft.title)
                        b.etContent.setText(draft.content)
                        if (draft.tags.isNotBlank()) b.etTags.setText(draft.tags)
                        selectedMood = draft.mood
                        selectedWeather = draft.weather
                        buildChips(b.moodRow, DiaryDbHelper.MOODS, selectedMood, ::onMoodTapped)
                        buildChips(b.weatherRow, DiaryDbHelper.WEATHERS, selectedWeather, ::onWeatherTapped)
                        updateWordCount()
                    }
                    .show()
            }
        }

        buildChips(b.moodRow, DiaryDbHelper.MOODS, selectedMood, ::onMoodTapped)
        buildChips(b.weatherRow, DiaryDbHelper.WEATHERS, selectedWeather, ::onWeatherTapped)

        b.btnBack.setOnClickListener { finishWithSaveCheck() }
        b.btnSave.setOnClickListener { save(exit = true) }
        b.btnDelete.setOnClickListener { confirmDelete() }

        // 输入停顿 800ms 就存一次草稿：切走 App、被系统杀进程都不会丢字
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                dirty = true
                updateWordCount()
                scheduleDraft()
            }
        }
        b.etContent.addTextChangedListener(watcher)
        b.etTitle.addTextChangedListener(watcher)
        b.etTags.addTextChangedListener(watcher)

        updateWordCount()

        if (existing == null) {
            b.etTitle.requestFocus()
        }
    }

    // ---------------- 字数统计 ----------------

    private fun updateWordCount() {
        val text = b.etContent.text?.toString().orEmpty()
        // 中文按字算，连续英文/数字算一个词；粗略但符合写日记的直觉
        val chars = text.count { !it.isWhitespace() }
        b.tvCount.text = if (chars == 0) "" else "正文 $chars 字"
    }

    // ---------------- 草稿 ----------------

    private val draftHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val draftTask = Runnable { persistDraft() }

    private fun scheduleDraft() {
        draftHandler.removeCallbacks(draftTask)
        draftHandler.postDelayed(draftTask, 800)
    }

    private fun persistDraft() {
        val title = b.etTitle.text?.toString().orEmpty()
        val content = b.etContent.text?.toString().orEmpty()
        if (title.isBlank() && content.isBlank()) {
            db.clearDraft(date)
            return
        }
        db.saveDraft(
            Diary(
                date = date,
                title = title,
                content = content,
                mood = selectedMood,
                weather = selectedWeather,
                tags = b.etTags.text?.toString().orEmpty()
            )
        )
    }

    /** 切后台/离开页面时立刻落盘，不依赖那 800ms 的延迟 */
    override fun onPause() {
        super.onPause()
        draftHandler.removeCallbacks(draftTask)
        persistDraft()
    }

    private fun onMoodTapped(key: String) {
        selectedMood = if (selectedMood == key) "" else key
        buildChips(b.moodRow, DiaryDbHelper.MOODS, selectedMood, ::onMoodTapped)
        dirty = true
    }

    private fun onWeatherTapped(key: String) {
        selectedWeather = if (selectedWeather == key) "" else key
        buildChips(b.weatherRow, DiaryDbHelper.WEATHERS, selectedWeather, ::onWeatherTapped)
        dirty = true
    }

    /** 生成一排可点选的心情/天气胶囊 */
    private fun buildChips(
        row: LinearLayout,
        items: List<Triple<String, String, String>>,
        selected: String,
        onSelect: (String) -> Unit
    ) {
        row.removeAllViews()
        val density = resources.displayMetrics.density
        items.forEach { (key, label, emoji) ->
            val isSel = key == selected
            val tv = TextView(this)
            tv.text = "$emoji $label"
            tv.textSize = 13f
            tv.gravity = Gravity.CENTER
            tv.setPadding(
                (14 * density).toInt(), (8 * density).toInt(),
                (14 * density).toInt(), (8 * density).toInt()
            )
            tv.setBackgroundResource(if (isSel) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            tv.setTextColor(if (isSel) Color.WHITE else getColor(R.color.ink_soft))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = (8 * density).toInt()
            tv.layoutParams = lp
            tv.setOnClickListener { onSelect(key) }
            row.addView(tv)
        }
    }

    private fun save(exit: Boolean) {
        val content = b.etContent.text?.toString()?.trim().orEmpty()
        val title = b.etTitle.text?.toString()?.trim().orEmpty()
        val tags = b.etTags.text?.toString()?.trim().orEmpty()
            .split(",", "，")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

        if (content.isEmpty() && title.isEmpty()) {
            if (exit) {
                // 空内容：如果原本有日记，问是否删除；否则直接退出
                if (existing != null) confirmDelete() else finish()
            }
            return
        }

        db.upsert(
            Diary(
                date = date,
                title = title,
                content = content,
                mood = selectedMood,
                weather = selectedWeather,
                tags = tags
            )
        )
        // 已经正式保存，草稿使命完成
        db.clearDraft(date)
        dirty = false
        if (exit) {
            b.tvDate.postDelayed({
                android.widget.Toast.makeText(this, "已保存", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }, 60)
        }
    }

    private fun finishWithSaveCheck() {
        val hasText = !b.etContent.text.isNullOrBlank() || !b.etTitle.text.isNullOrBlank()
        if (hasText) save(exit = true) else finish()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除日记")
            .setMessage(getString(R.string.confirm_delete))
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                db.deleteByDate(date)
                dirty = false
                finish()
            }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithSaveCheck()
    }
}
