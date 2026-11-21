package org.sketchware.daygreen.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pro.sketchware.R
import pro.sketchware.utility.SketchwareUtil
import java.util.Locale
import java.util.regex.Pattern

class ChatAdapter(
    private val formatter: ChatTextFormatter,
    private val onEventClick: ChatTextFormatter.OnEventClick
) : RecyclerView.Adapter<ChatAdapter.Holder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AGENT = 1
        private const val TYPE_STATUS = 2
        private const val TYPE_THINKING = 3
        private const val TYPE_TASK = 4

        private val FENCE = Pattern.compile("```([A-Za-z0-9_+#.\\-:\\s]*?)\\n([\\s\\S]*?)```")
    }

    data class TaskStep(
        val title: String,
        var isFinished: Boolean = false,
        var detail: String? = null
    )

    class ChatMessage private constructor(
        val type: Int,
        var text: String,
        var expanded: Boolean = false,
        val steps: MutableList<TaskStep> = mutableListOf(),
        var isTaskFinished: Boolean = false
    ) {
        companion object {
            fun user(text: String) = ChatMessage(TYPE_USER, text)
            fun agent(text: String) = ChatMessage(TYPE_AGENT, text)
            fun status(text: String) = ChatMessage(TYPE_STATUS, text)
            fun thinking(text: String) = ChatMessage(TYPE_THINKING, text)
            fun task(title: String) = ChatMessage(TYPE_TASK, title, expanded = false)
        }
    }

    private val items = mutableListOf<ChatMessage>()

    fun add(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    fun updateStatus(text: String) {
        removeStatus()
        add(ChatMessage.status(text))
    }

    fun removeStatus() {
        for (i in items.indices.reversed()) {
            if (items[i].type == TYPE_STATUS) {
                items.removeAt(i)
                notifyItemRemoved(i)
            }
        }
    }

    fun updateTaskStep(stepTitle: String, isFinished: Boolean, detail: String?) {
        // Find existing task message or create a new one
        var taskMsg: ChatMessage? = null
        var taskIndex = -1
        for (i in items.indices.reversed()) {
            if (items[i].type == TYPE_TASK && !items[i].isTaskFinished) {
                taskMsg = items[i]
                taskIndex = i
                break
            }
        }

        if (taskMsg == null) {
            removeStatus()
            taskMsg = ChatMessage.task("Executing Task...")
            items.add(taskMsg)
            taskIndex = items.size - 1
            notifyItemInserted(taskIndex)
        }

        // Check if step exists or append new
        val existing = taskMsg.steps.find { it.title == stepTitle }
        if (existing != null) {
            existing.isFinished = isFinished
            if (detail != null) existing.detail = detail
        } else {
            taskMsg.steps.add(TaskStep(stepTitle, isFinished, detail))
        }

        notifyItemChanged(taskIndex)
    }

    fun finishActiveTask() {
        for (i in items.indices.reversed()) {
            if (items[i].type == TYPE_TASK && !items[i].isTaskFinished) {
                items[i].isTaskFinished = true
                items[i].text = "Completed Task"
                items[i].expanded = false // Auto-collapse into compact markdown pill
                notifyItemChanged(i)
                break
            }
        }
    }

    override fun getItemViewType(position: Int): Int = items[position].type

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = when (viewType) {
            TYPE_USER -> R.layout.item_chat_user
            TYPE_STATUS -> R.layout.item_chat_status
            TYPE_THINKING -> R.layout.item_chat_thinking
            TYPE_TASK -> R.layout.item_chat_task
            else -> R.layout.item_chat_agent
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        when (item.type) {
            TYPE_USER -> holder.text?.text = item.text
            TYPE_AGENT -> bindAgent(holder, item)
            TYPE_STATUS -> holder.text?.text = item.text
            TYPE_THINKING -> bindThinking(holder, item)
            TYPE_TASK -> bindTask(holder, item)
        }
    }

    private fun bindAgent(holder: Holder, item: ChatMessage) {
        val parts = holder.parts ?: return
        parts.removeAllViews()
        val inflater = LayoutInflater.from(parts.context)

        for (seg in splitSegments(item.text)) {
            if (seg.isCode) {
                parts.addView(buildCodePart(inflater, parts, seg.lang, seg.text))
            } else {
                val plain = seg.text.trim()
                if (plain.isNotEmpty()) {
                    parts.addView(buildTextPart(inflater, parts, plain))
                }
            }
        }
    }

    private fun buildTextPart(inflater: LayoutInflater, parent: ViewGroup, text: String): View {
        val view = inflater.inflate(R.layout.item_chat_text, parent, false)
        val tv = view.findViewById<TextView>(R.id.tv_message)
        tv.text = formatter.format(text, onEventClick)
        tv.movementMethod = LinkMovementMethod.getInstance()
        return view
    }

    private fun buildCodePart(inflater: LayoutInflater, parent: ViewGroup, rawLang: String?, code: String): View {
        val view = inflater.inflate(R.layout.item_chat_code, parent, false)
        var lang = rawLang?.trim() ?: ""
        if (lang.contains(" ") || lang.contains(":")) {
            val parts = lang.split(Regex("[:\\s]+"), 2)
            lang = parts[0].trim()
        }
        val displayLang = if (lang.isEmpty()) "code" else lang.lowercase(Locale.ROOT)

        val langTv = view.findViewById<TextView>(R.id.tv_lang)
        langTv?.text = displayLang

        val codeTv = view.findViewById<TextView>(R.id.tv_code)
        codeTv.text = ChatCodeHighlighter.highlight(code)

        val copy = view.findViewById<ImageButton>(R.id.btn_copy)
        copy.setOnClickListener { v ->
            val ctx = v.context
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(displayLang, code))
                SketchwareUtil.toast("Code copied to clipboard")
            }
        }
        return view
    }

    private fun bindThinking(holder: Holder, item: ChatMessage) {
        holder.title?.text = "Reasoning (${if (item.expanded) "hide" else "show"})"
        holder.chevron?.rotation = if (item.expanded) 180f else 0f
        holder.body?.text = item.text
        holder.body?.visibility = if (item.expanded) View.VISIBLE else View.GONE

        val toggle = View.OnClickListener {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@OnClickListener
            item.expanded = !item.expanded
            notifyItemChanged(pos)
        }

        if (holder.thinkingHeader != null) {
            holder.thinkingHeader.setOnClickListener(toggle)
        } else {
            holder.title?.setOnClickListener(toggle)
        }
    }

    private fun bindTask(holder: Holder, item: ChatMessage) {
        val count = item.steps.size
        val badgeText = if (count == 1) "1 step" else "$count steps"
        holder.taskBadge?.text = badgeText

        if (item.isTaskFinished) {
            holder.taskProgress?.visibility = View.GONE
            holder.taskDoneIcon?.visibility = View.VISIBLE
            holder.taskTitle?.text = if (item.expanded) "Completed Task ($badgeText)" else "Task Completed ($badgeText)"
        } else {
            holder.taskProgress?.visibility = View.VISIBLE
            holder.taskDoneIcon?.visibility = View.GONE
            holder.taskTitle?.text = item.text
        }

        holder.taskChevron?.rotation = if (item.expanded) 180f else 0f
        holder.taskBody?.visibility = if (item.expanded) View.VISIBLE else View.GONE

        // Populate steps in container
        val stepsContainer = holder.taskSteps
        if (stepsContainer != null) {
            stepsContainer.removeAllViews()
            val inflater = LayoutInflater.from(stepsContainer.context)
            for (step in item.steps) {
                val stepView = inflater.inflate(R.layout.item_chat_task_step, stepsContainer, false)
                val ivIcon = stepView.findViewById<ImageView>(R.id.iv_step_icon)
                val tvTitle = stepView.findViewById<TextView>(R.id.tv_step_title)
                val tvDetail = stepView.findViewById<TextView>(R.id.tv_step_detail)

                tvTitle.text = step.title
                if (step.isFinished) {
                    ivIcon.setImageResource(R.drawable.ic_mtrl_done)
                    ivIcon.alpha = 1.0f
                } else {
                    ivIcon.setImageResource(R.drawable.check_indeterminate_small_24px)
                    ivIcon.alpha = 0.6f
                }

                if (!step.detail.isNullOrEmpty()) {
                    tvDetail.text = step.detail
                    tvDetail.visibility = View.VISIBLE
                } else {
                    tvDetail.visibility = View.GONE
                }

                stepsContainer.addView(stepView)
            }
        }

        val toggle = View.OnClickListener {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@OnClickListener
            item.expanded = !item.expanded
            notifyItemChanged(pos)
        }

        holder.taskHeader?.setOnClickListener(toggle)
    }

    class Segment private constructor(
        val isCode: Boolean,
        val lang: String,
        val text: String
    ) {
        companion object {
            fun plain(text: String) = Segment(false, "", text)
            fun code(lang: String, text: String) = Segment(true, lang, text)
        }
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView? = itemView.findViewById(R.id.tv_message)
        val parts: LinearLayout? = itemView.findViewById(R.id.ll_parts)
        val thinkingHeader: View? = itemView.findViewById(R.id.ll_thinking_header)
        val chevron: ImageView? = itemView.findViewById(R.id.iv_thinking_chevron)
        val title: TextView? = itemView.findViewById(R.id.tv_thinking_title)
        val body: TextView? = itemView.findViewById(R.id.tv_thinking_body)

        // Task Holder views
        val taskHeader: View? = itemView.findViewById(R.id.ll_task_header)
        val taskTitle: TextView? = itemView.findViewById(R.id.tv_task_title)
        val taskBadge: TextView? = itemView.findViewById(R.id.tv_task_badge)
        val taskProgress: View? = itemView.findViewById(R.id.pi_task_progress)
        val taskDoneIcon: View? = itemView.findViewById(R.id.iv_task_done)
        val taskChevron: ImageView? = itemView.findViewById(R.id.iv_task_chevron)
        val taskBody: View? = itemView.findViewById(R.id.ll_task_body)
        val taskSteps: LinearLayout? = itemView.findViewById(R.id.ll_task_steps)

        init {
            text?.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun splitSegments(text: String): List<Segment> {
        val out = mutableListOf<Segment>()
        val m = FENCE.matcher(text)
        var last = 0
        while (m.find()) {
            if (m.start() > last) {
                out.add(Segment.plain(text.substring(last, m.start())))
            }
            val code = m.group(2)!!.replace(Regex("\\n+$"), "")
            out.add(Segment.code(m.group(1) ?: "", code))
            last = m.end()
        }
        if (last < text.length) {
            out.add(Segment.plain(text.substring(last)))
        }
        return out
    }
}
