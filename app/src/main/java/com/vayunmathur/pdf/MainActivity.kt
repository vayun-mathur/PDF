package com.vayunmathur.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.util.forEach
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPoint
import androidx.pdf.PdfRect
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.PdfViewerState
import androidx.pdf.data.DisplayData
import androidx.pdf.view.Highlight
import androidx.pdf.viewer.loader.PdfLoader
import com.vayunmathur.pdf.ui.theme.PDFTheme
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get the intent that started this activity
        val intent: Intent = intent
        val data: Uri? = intent.data

        val pdfLoader = SandboxedPdfLoader(this)


        setContent {
            var pdfDocument: PdfDocument? by remember { mutableStateOf(null) }
            val scope = rememberCoroutineScope()

            val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                it?.let {
                    scope.launch {
                        pdfDocument = pdfLoader.openDocument(it)
                    }
                }
                if(it == null) {
                    finish()
                }
            }

            LaunchedEffect(Unit) {
                if(data != null && (intent.action == Intent.ACTION_VIEW)) {
                    pdfDocument = pdfLoader.openDocument(data)
                } else {
                    filePickerLauncher.launch(arrayOf("application/pdf"))
                }
            }
            PDFTheme {
                pdfDocument?.let {
                    PdfViewerScreen(it)
                }
                if(pdfDocument == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(pdfDocument: PdfDocument) {
    val pdfState = remember { PdfViewerState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    var showSearchBar by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<PdfRect>()) }
    var searchIndex by remember(searchResults) { mutableIntStateOf(0) }
    var searchText by remember { mutableStateOf("") }
    BackHandler(showSearchBar) {
        showSearchBar = false
        searchResults = emptyList()
    }
    fun search() {
        scope.launch {
            val results = pdfDocument.searchDocument(searchText, 0 until pdfDocument.pageCount)
            val resultsFinal = mutableListOf<PdfRect>()
            results.forEach { page, result ->
                resultsFinal += result.map { PdfRect(page, it.bounds[0]) }
            }
            searchResults = resultsFinal
        }
    }
    LaunchedEffect(searchResults, searchIndex) {
        pdfState.setHighlights(
            searchResults.mapIndexed { idx, it ->
                Highlight(it, if(idx == searchIndex) 0xFFFFA500.toInt() else Color.Yellow.toArgb())
            }
        )
        if(searchResults.isNotEmpty()) {
            pdfState.scrollToPosition(searchResults[searchIndex].let {
                PdfPoint(it.pageNum, it.left, it.top)
            })
        }
    }
    val focusRequestor = remember { FocusRequester() }
    LaunchedEffect(showSearchBar) {
        if(showSearchBar) {
            focusRequestor.requestFocus()
            search()
        } else {
            searchResults = emptyList()
        }
    }
    Scaffold(modifier = Modifier.fillMaxSize().imePadding(), topBar = {
        TopAppBar({ Text("PDF Viewer") }, actions = {
            if(!showSearchBar) {
                IconButton({
                    showSearchBar = true
                }) {
                    Icon(Icons.Default.Search, null)
                }
                IconButton({
                    // share
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "application/pdf"
                    intent.putExtra(Intent.EXTRA_STREAM, pdfDocument.uri)
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Share, null)
                }
            } else {
                if(searchResults.isNotEmpty()) {
                    Text("${searchIndex + 1} of ${searchResults.size}   ")
                }
            }
        })
    }, bottomBar = {
        if(showSearchBar) {
            BottomAppBar {
                OutlinedTextField(
                    searchText,
                    { searchText = it; search() },
                    Modifier.fillMaxWidth().focusRequester(focusRequestor),
                    label = { Text("Find") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                )
            }
        }
    }, floatingActionButton = {
        if(showSearchBar) {
            Column {
                SmallFloatingActionButton({
                    if(searchIndex > 0)
                        searchIndex--
                }) {
                    Icon(Icons.Default.KeyboardArrowUp, null)
                }
                SmallFloatingActionButton({
                    if(searchIndex < searchResults.size - 1)
                        searchIndex++
                }) {
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
            }
        }
    }) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            PdfViewer(pdfDocument, pdfState) { uri ->
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
                true
            }
        }
    }
}