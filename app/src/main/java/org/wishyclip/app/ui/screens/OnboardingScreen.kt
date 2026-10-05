package org.wishyclip.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wishyclip.app.R
import org.wishyclip.app.ui.design.WishyIcons
import org.wishyclip.app.ui.design.WishyTheme
import org.wishyclip.app.ui.design.themes.LightTokens

data class OnboardingPage(val iconRes: Int, val titleRes: Int, val descRes: Int)

private val Pages = listOf(
    OnboardingPage(WishyIcons.Pen, R.string.onboarding_1_title, R.string.onboarding_1_desc),
    OnboardingPage(WishyIcons.Play, R.string.onboarding_2_title, R.string.onboarding_2_desc),
    OnboardingPage(WishyIcons.Export, R.string.onboarding_3_title, R.string.onboarding_3_desc)
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val tokens = WishyTheme.tokens
    var currentPage by remember { mutableIntStateOf(0) }

    WishyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = tokens.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(tokens.spaceLarge)
            ) {
                val page = Pages[currentPage]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceLarge)
                ) {
                    Image(
                        painter = painterResource(page.iconRes),
                        contentDescription = stringResource(page.titleRes),
                        modifier = Modifier.size(120.dp)
                    )
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(page.descRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onFinish) {
                        Text(text = "Skip")
                    }
                    Button(
                        onClick = {
                            if (currentPage < Pages.lastIndex) {
                                currentPage++
                            } else {
                                onFinish()
                            }
                        }
                    ) {
                        Text(text = if (currentPage < Pages.lastIndex) "Next" else "Get Started")
                    }
                }
            }
        }
    }
}

@Preview(name = "OnboardingScreen Preview")
@Composable
private fun OnboardingScreenPreview() {
    WishyTheme(tokens = LightTokens) {
        OnboardingScreen(onFinish = {})
    }
}
