package com.alicia.cloudstorage.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alicia.cloudstorage.phone.data.ApiException
import com.alicia.cloudstorage.phone.data.RagFeedbackCategory
import com.alicia.cloudstorage.phone.data.RagFeedbackReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class FeedbackStep { CATEGORY, OFFER_SAMPLE, EDIT, REVIEW, SAVED }
private const val MAX_EXPRESSION_LENGTH = 400

@Composable
internal fun RagRecognitionFeedbackDialog(
    reference: RagFeedbackReference,
    reported: Boolean,
    report: suspend (RagFeedbackCategory) -> Unit,
    preview: suspend (String) -> String,
    submit: suspend (String) -> String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember(reference) {
        mutableStateOf(if (reported) FeedbackStep.EDIT else FeedbackStep.CATEGORY)
    }
    var category by remember(reference) { mutableStateOf<RagFeedbackCategory?>(null) }
    var draft by remember(reference) { mutableStateOf("") }
    var reviewed by remember(reference) { mutableStateOf("") }
    var consent by remember(reference) { mutableStateOf(false) }
    var receipt by remember(reference) { mutableStateOf("") }
    var busy by remember(reference) { mutableStateOf(false) }
    var error by remember(reference) { mutableStateOf<String?>(null) }

    fun runRequest(failure: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rejected: ApiException) {
                error = when (rejected.status) {
                    401, 403 -> "登录已失效或权限有变化，请重新登录。"
                    404 -> "这条追踪记录已不可用，可以对新回复提交反馈。"
                    409 -> "这条反馈的状态已变化，请结束后重新查看。"
                    422 -> "样本无法通过校验，请修改后重新预览。"
                    else -> failure
                }
            } catch (_: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(when (step) {
                FeedbackStep.CATEGORY -> "识别反馈"
                FeedbackStep.OFFER_SAMPLE -> "反馈已提交"
                FeedbackStep.EDIT -> "补充脱敏表达"
                FeedbackStep.REVIEW -> "确认提交样本"
                FeedbackStep.SAVED -> "样本已提交"
            })
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (step) {
                    FeedbackStep.CATEGORY -> {
                        Text("只提交所选评价和追踪编号。")
                        Column(Modifier.selectableGroup()) {
                            RagFeedbackCategory.entries.forEach { value ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(
                                        selected = category == value,
                                        enabled = !busy,
                                        role = Role.RadioButton,
                                        onClick = { category = value },
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RadioButton(selected = category == value, onClick = null, enabled = !busy)
                                    Text(value.label)
                                }
                            }
                        }
                    }
                    FeedbackStep.OFFER_SAMPLE -> Text("你也可以提供一条脱敏表达，帮助复现这个问题。")
                    FeedbackStep.EDIT -> {
                        Text("请自行改写，使用【文件】【目录】【人员】替换真实名称，并删除业务内容。预览会把你填写的草稿发给服务端进行脱敏检查。")
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { if (it.length <= MAX_EXPRESSION_LENGTH) draft = it },
                            enabled = !busy,
                            label = { Text("脱敏表达") },
                            supportingText = { Text("${draft.length}/$MAX_EXPRESSION_LENGTH") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                        )
                    }
                    FeedbackStep.REVIEW -> {
                        Text(reviewed)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                value = consent, enabled = !busy, role = Role.Checkbox,
                                onValueChange = { consent = it },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = consent, onCheckedChange = null, enabled = !busy)
                            Text("我已检查这段表达，授权用于识别评测。")
                        }
                    }
                    FeedbackStep.SAVED -> {
                        Text("如需撤回样本，可向维护人员提供此编号。")
                        SelectionContainer { Text(receipt) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (step) {
                FeedbackStep.CATEGORY -> TextButton(
                    enabled = category != null && !busy,
                    onClick = {
                        val selected = category ?: return@TextButton
                        runRequest("反馈暂时无法提交，请稍后重试。") {
                            report(selected)
                            if (selected == RagFeedbackCategory.CORRECT || !reference.candidateEnabled) onDismiss()
                            else step = FeedbackStep.OFFER_SAMPLE
                        }
                    },
                ) { Text(if (busy) "正在提交…" else "提交反馈") }
                FeedbackStep.OFFER_SAMPLE -> TextButton(onClick = { step = FeedbackStep.EDIT }) {
                    Text("补充样本")
                }
                FeedbackStep.EDIT -> TextButton(
                    enabled = draft.isNotBlank() && !busy,
                    onClick = {
                        runRequest("无法生成预览，请稍后重试。") {
                            reviewed = preview(draft)
                            draft = reviewed
                            consent = false
                            step = FeedbackStep.REVIEW
                        }
                    },
                ) { Text(if (busy) "正在检查…" else "脱敏预览") }
                FeedbackStep.REVIEW -> TextButton(
                    enabled = consent && !busy,
                    onClick = {
                        runRequest("样本暂时无法提交，请稍后重试。") {
                            receipt = submit(reviewed)
                            reviewed = ""
                            draft = ""
                            step = FeedbackStep.SAVED
                        }
                    },
                ) { Text(if (busy) "正在提交…" else "授权并提交") }
                FeedbackStep.SAVED -> TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
        dismissButton = {
            if (step != FeedbackStep.SAVED) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (step == FeedbackStep.REVIEW) {
                            consent = false
                            error = null
                            step = FeedbackStep.EDIT
                        } else {
                            onDismiss()
                        }
                    },
                ) {
                    Text(when (step) {
                        FeedbackStep.CATEGORY -> "取消"
                        FeedbackStep.REVIEW -> "返回修改"
                        else -> "完成"
                    })
                }
            }
        },
    )
}
