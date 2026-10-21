package com.ripple.chat.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import com.ripple.chat.utils.LinkSafetyChecker

@Composable
fun MessageLinkText(
    text: String,
    textColor: Color,
    linkColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val links = remember(text) { LinkSafetyChecker.extractLinks(text) }

    if (links.isEmpty()) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = modifier
        )
        return
    }

    val annotatedString = remember(text, linkColor) {
        buildAnnotatedString {
            var currentIndex = 0
            for (link in links) {
                if (link.startIndex > currentIndex) {
                    append(text.substring(currentIndex, link.startIndex))
                }

                val linkAnnotation = LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    linkInteractionListener = { annotation ->
                        val url = (annotation as? LinkAnnotation.Url)?.url ?: link.url
                        onUrlClick(url)
                    }
                )

                withLink(linkAnnotation) {
                    append(text.substring(link.startIndex, link.endIndex))
                }

                currentIndex = link.endIndex
            }

            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }

    Text(
        text = annotatedString,
        color = textColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier
    )
}
