// app/src/main/java/com/luminar/reader/presentation/onboarding/OnboardingScreen.kt
package com.luminar.reader.presentation.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminar.reader.R
import com.luminar.reader.presentation.theme.LuminarGold
import com.luminar.reader.presentation.theme.LuminarTitleFont
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val icon: Int,
    val iconSize: Int = 64,
    val accentEmoji: String = ""
)

private val pages = listOf(
    OnboardingPage(
        title = "Every format.\nOne app.",
        subtitle = "PDF, EPUB, DOCX, Markdown, code, comics, spreadsheets — 30+ formats that each render like their native app.",
        icon = R.drawable.ic_auto_stories_48,
        iconSize = 72
    ),
    OnboardingPage(
        title = "Your reading,\nyour way.",
        subtitle = "AMOLED dark, warm sepia, or clean white. Adjustable fonts, text-to-speech with 7 voice profiles, bookmarks, and search.",
        icon = R.drawable.ic_palette_24,
        iconSize = 56
    ),
    OnboardingPage(
        title = "Built different.",
        subtitle = "No ads. No subscriptions. No tracking. No cloud uploads. Your files stay on your device, always.",
        icon = R.drawable.ic_settings_24,
        iconSize = 48
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Skip button (top-right)
        TextButton(
            onClick = onComplete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(
                text = "Skip",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    isActive = pagerState.currentPage == page
                )
            }

            // Page indicators
            Row(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    PageIndicator(isActive = pagerState.currentPage == index)
                }
            }

            // Bottom button
            val buttonText = if (isLastPage) "Get started" else "Continue"

            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LuminarGold,
                    contentColor = Color(0xFF171100)
                )
            ) {
                Text(
                    text = buttonText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(0.08f))
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    isActive: Boolean
) {
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pageAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon in gold circle
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = LuminarGold.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(page.icon),
                contentDescription = null,
                modifier = Modifier.size(page.iconSize.dp),
                tint = LuminarGold
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Title
        Text(
            text = page.title,
            fontFamily = LuminarTitleFont,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Subtitle
        Text(
            text = page.subtitle,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.8f)
        )
    }
}

@Composable
private fun PageIndicator(isActive: Boolean) {
    val width by animateDpAsState(
        targetValue = if (isActive) 28.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "indicatorWidth"
    )

    Box(
        modifier = Modifier
            .height(8.dp)
            .width(width)
            .clip(CircleShape)
            .background(
                if (isActive) LuminarGold
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            )
    )
}
