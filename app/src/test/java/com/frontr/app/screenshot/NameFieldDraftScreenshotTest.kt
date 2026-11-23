package com.frontr.app.screenshot

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frontr.app.ui.component.BoldButton
import com.frontr.app.ui.component.DialogNameField
import com.frontr.app.ui.component.DialogPane
import com.frontr.app.ui.component.QuietButton
import com.frontr.app.ui.component.rememberDialogNameState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 12), nothing of app/src/main changes: the name field of the New folder
 * dialog. Frames: today (FoldersScreen's NameDialog, a plain TextField with a placeholder, text centred by the dialog's
 * text slot), then the kit's field (ZoneDialog.kt DialogNameField and rememberDialogNameState: an outlined field, the
 * label "Folder name" in the field, rising onto the outline when touched, the cursor at the start) empty, touched
 * and typed "Android news", and Rename with the current name selected whole.
 * A ZoneAlertDialog is a window of its own, so five of them cannot share one picture: each frame is the dialog's pane
 * (the app's real DialogPane, the same glass) with a local copy of ZoneAlertContent's layout (24 dp, title in
 * headlineSmall, buttons sharing the width, the app's BoldButton and QuietButton), all centred as it is.
 * The empty frames (untouched, touched) draw the same outlined field as DialogNameField with an interaction source,
 * because Robolectric's window never takes focus: the kit's field is used as it is for the typed and Rename frames.
 * Pictures: name_field_draft (today, empty, touched), name_field_draft_typed (typed, Rename), each with its 200 % text
 * and 360 dp edges.
 * Compared: Android Files' DocumentsUI New folder dialog (outlined TextInputLayout, hint floats as a label, focus on
 * open, Done creates, maxLength 255), Google Drive's New folder, iOS Files.
 * Port of MTGA's validated draft:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/NameFieldDraftScreenshotTest.kt#Frames
 * https://github.com/LineageOS/android_packages_apps_DocumentsUI/blob/lineage-24.0/src/com/android/documentsui/CreateDirectoryFragment.java#onCreateDialog
 * https://github.com/LineageOS/android_packages_apps_DocumentsUI/blob/lineage-24.0/res/layout/dialog_file_name.xml#input_wrapper
 * not ported: DocumentsUI's createDirectory, onShowDialog, the error of a name taken and ensureKeyboardPresent (no
 * keyboard in a picture); the other samples of TextFieldSamples
 */
@OptIn(ExperimentalLayoutApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class NameFieldDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun draft() = capture("name_field_draft") { Frames(first = true) }

    // A phone is too short for five dialogs: the typed and Rename frames are a second picture.
    @Test
    fun draftTyped() = capture("name_field_draft_typed") { Frames(first = false) }

    @Test
    fun edge() = captureEdge("name_field_draft") { Frames(first = true) }

    @Test
    fun edgeTyped() = captureEdge("name_field_draft_typed") { Frames(first = false) }

    // https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/NameFieldDraftScreenshotTest.kt#Frames
    @Composable
    private fun Frames(first: Boolean) {
        FlowRow(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (first) {
                Frame("New folder", "Create") { TodayField() }
                Frame("New folder", "Create") { ProposedField(focus = false) }
                Frame("New folder", "Create") { ProposedField(focus = true) }
            } else {
                Frame("New folder", "Create") {
                    DialogNameField(state = rememberDialogNameState("Android news"), label = "Folder name", onDone = {})
                }
                Frame("Rename", "Rename") {
                    DialogNameField(state = rememberDialogNameState("Android news", selectAll = true), label = "Folder name", onDone = {})
                }
            }
        }
    }

    // https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/NameFieldDraftScreenshotTest.kt#Frame
    @Composable
    private fun Frame(title: String, confirm: String, field: @Composable () -> Unit) {
        Box(Modifier.widthIn(min = 280.dp, max = 560.dp)) {
            DialogPane {
                Column(Modifier.padding(24.dp)) {
                    Box(Modifier.padding(bottom = 16.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                    // ZoneAlertContent's text slot (ZoneDialog.kt): bodyMedium merged in, text centred.
                    ProvideTextStyle(LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium).copy(textAlign = TextAlign.Center)) {
                        Box(Modifier.padding(bottom = 24.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { field() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f), propagateMinConstraints = true) { QuietButton(onClick = {}) { Text("Cancel") } }
                        Box(Modifier.weight(1f), propagateMinConstraints = true) {
                            BoldButton(onClick = {}, filled = true) { Text(confirm) }
                        }
                    }
                }
            }
        }
    }

    // NameDialog's field today (FoldersScreen.kt): a TextField with a placeholder, focused.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/NameFieldDraftScreenshotTest.kt#TodayField
    @Composable
    private fun TodayField() {
        var name by remember { mutableStateOf("") }
        val source = remember { MutableInteractionSource() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            source.emit(FocusInteraction.Focus())
        }
        TextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            placeholder = { Text("Folder name") },
            interactionSource = source,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // DialogNameField's own outlined field, empty, with an interaction source so the picture can show it touched: the
    // label rests in the field, or rises onto the outline.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/NameFieldDraftScreenshotTest.kt#ProposedField
    @Composable
    private fun ProposedField(focus: Boolean) {
        val state = rememberDialogNameState()
        val requester = remember { FocusRequester() }
        // Robolectric's window never takes focus, so requestFocus alone draws no focused state: the same focus is
        // also sent through the interaction source, which is what the field reads to draw it (Material's previews do it).
        val source = remember { MutableInteractionSource() }
        if (focus) LaunchedEffect(Unit) {
            withFrameNanos { }
            requester.requestFocus()
            withFrameNanos { }
            source.emit(FocusInteraction.Focus())
        }
        OutlinedTextField(
            state = state,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text("Folder name") },
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            inputTransformation = InputTransformation.maxLength(255),
            interactionSource = source,
            modifier = Modifier.fillMaxWidth().focusRequester(requester)
        )
    }
}
