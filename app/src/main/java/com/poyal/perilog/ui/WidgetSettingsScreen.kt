package com.poyal.perilog.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.poyal.perilog.widget.*

@Composable fun WidgetSettingsScreen(back:()->Unit) {
    val context=LocalContext.current
    var message by remember { mutableStateOf("") }
    fun pin(provider:Class<*>) {
        val manager=AppWidgetManager.getInstance(context)
        message=if(manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context,provider),null,null))
            "홈 화면의 추가 확인 창에서 위젯을 배치해 주세요."
        else "휴대폰 홈 화면의 빈 곳을 길게 누른 뒤 위젯 → 페리로그에서 추가해 주세요."
    }
    Page("홈 화면 위젯","휴대폰 홈에서 기록과 일정을 바로 확인해요",back) {
        Paper {
            Section("어제·오늘 기록")
            Hint("4×1·4×2에서는 어제·오늘을 나란히, 2×2에서는 위아래로 표시해요. 높이를 한 칸으로 줄이면 각 날짜의 세 단계를 가로로, 높이가 넉넉하면 세로로 펼쳐요. 활력 상태·사용 구성·투석 기록의 세 단계를 확인하고 날짜를 눌러 이어서 작성해요.")
            Action("기록 위젯 추가",{pin(DailyRecordWidgetReceiver::class.java)})
        }
        Paper {
            Section("병원 일정")
            Hint("2×2에서 가장 가까운 방문의 D-day·진료과별 시간·처치를 한눈에 확인해요. 메모는 표시하지 않아요. 일정을 누르면 해당 예약을 열어요.")
            Action("일정 위젯 추가",{pin(AppointmentWidgetReceiver::class.java)})
        }
        if(message.isNotEmpty())Paper {Hint(message)}
        Paper {
            Section("사용 방법")
            Hint("위젯을 길게 누르면 크기를 조절하거나 삭제할 수 있어요. 위젯 안에는 스크롤이 없고, 공간에 맞춰 배치·글씨·간격을 조절해요. 처치는 한 열 또는 두 열로 모두 표시해요. 처치가 많아 작게 보이면 위젯 크기를 늘려 주세요.")
            Hint("앱의 변경 내용은 자동으로 반영돼요. 오른쪽 위 새로고침 버튼으로도 갱신할 수 있어요. 절전 상태에서는 갱신이 늦어질 수 있어요.")
            Hint("앱 잠금을 켜면 위젯 내용도 가려져요. 잠금 해제 후 앱에서 확인해 주세요.")
        }
    }
}
