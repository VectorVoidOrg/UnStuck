package com.ufo.fraudobserver.feature.unstuck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufo.fraudobserver.ui.theme.*

@Composable
fun DisclaimerBannerView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UfoBg)
            .border(1.dp, UfoBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "Regulatory Disclaimer",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = UfoTextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Unstuck is an automated assistance facility operating under RBI Circular DPSS.CO.PD No.629/02.01.014/2019-20. It provides informational support and legal draft generation. All final claims are subject to statutory verification by the respective bank or Ombudsman.",
                style = MaterialTheme.typography.bodySmall,
                color = UfoTextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

