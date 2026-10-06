package com.poyal.perilog

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.input.key.Key
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.asCareTask
import com.poyal.perilog.domain.departmentTime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.io.File

/** Run on a disposable test emulator. No screenshot capture or external phone/SMS dispatch. */
@RunWith(AndroidJUnit4::class)
class AppointmentFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<PerilogApplication>()
    private fun snapshot()=runBlocking{app.repository.snapshot()}
    private fun await(condition:()->Boolean)=ui.waitUntil(15000,condition)
    private fun node(text:String)=ui.onNodeWithText(text)
    private fun show(n:SemanticsNodeInteraction):SemanticsNodeInteraction {
        val parents=n.onAncestors().filter(hasScrollAction())
        for(index in parents.fetchSemanticsNodes().indices.reversed())runCatching{parents[index].performScrollTo()}
        runCatching{n.performScrollTo()};return n
    }
    private fun click(text:String){show(node(text)).performClick()}
    private fun contactMenu(name:String,action:String) {
        show(ui.onNodeWithContentDescription("$name 더보기")).performClick()
        ui.onNodeWithContentDescription("$name $action").performClick()
    }
    private fun input(label:String,value:String) {
        show(ui.onNode(hasSetTextAction() and hasText(label))).performTextReplacement(value)
        ui.runOnIdle{(ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0)}
        ui.waitForIdle()
    }
    private fun back(){ui.onNodeWithContentDescription("뒤로").performClick()}
    private fun settings(){ui.onNodeWithContentDescription("설정").performClick()}
    private fun avatar(name:String)=ui.onNodeWithContentDescription("$name 연락처")
    private fun closeContact(){click("닫기");await{ui.onAllNodesWithText("연결 방법 선택").fetchSemanticsNodes().isEmpty()}}
    private fun pickDate(date:LocalDate) {
        click("날짜 선택")
        if(ui.onAllNodesWithContentDescription(date.toString()).fetchSemanticsNodes().isEmpty())ui.onNodeWithContentDescription("다음 달").performClick()
        show(ui.onNodeWithContentDescription(date.toString())).performClick()
    }
    @Before fun fixture() {
        runBlocking{app.repository.restore(Snapshot(preferences=Preferences(darkMode="LIGHT",celebrate=false)))}
        await{ui.onAllNodesWithText("오늘 기록 시작").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun registerMultipleDepartmentsCareAndContactThenShowOnHome() {
        settings();node("병원·연락처").assertExists()
        click("진료과");click("+ 진료과 등록")
        input("진료과 이름","신장내과");click("저장");await{snapshot().departments.size==1}
        click("+ 진료과 등록");input("진료과 이름","내분비내과")
        show(ui.onNodeWithContentDescription("색상 #47956E")).performClick();click("저장")
        await{snapshot().departments.size==2};back()
        click("검사·치료 항목");click("+ 치료 항목 등록")
        input("치료 항목 이름","피검사");click("아이콘 · 진료")
        show(ui.onNodeWithContentDescription("피검사 아이콘")).performClick();click("저장")
        await{snapshot().careTemplates.size==1};click("+ 치료 항목 등록")
        input("치료 항목 이름","투석실 방문");click("아이콘 · 진료")
        show(ui.onNodeWithContentDescription("투석실 아이콘")).performClick();click("저장")
        await{snapshot().careTemplates.size==2};back();back()
        ui.onNodeWithContentDescription("병원 일정 추가").assertDoesNotExist()
        click("병원 일정 등록");pickDate(LocalDate.now().plusDays(1))
        click("신장내과");click("내분비내과")
        node("저장").assertIsNotEnabled()
        input("신장내과 예약시간 · HH:mm","09:30");input("내분비내과 예약시간 · HH:mm","24:00")
        node("저장").assertIsNotEnabled()
        click("내분비내과 시간 선택");click("확인")
        input("내분비내과 예약시간 · HH:mm","11:20")
        show(ui.onNodeWithContentDescription("피검사 치료 항목 선택")).performClick()
        show(ui.onNodeWithContentDescription("투석실 방문 치료 항목 선택")).performClick();input("메모","검사 후 투석실 방문")
        click("저장");await{snapshot().appointments.size==1}
        assertEquals(2,snapshot().appointments.single().departments.size)
        val saved=snapshot().appointments.single()
        assertEquals(listOf("09:30","11:20"),saved.departments.map{saved.departmentTime(it)})
        assertEquals(listOf("blood","dialysis"),snapshot().appointments.single().careItems.map{it.iconKey})
        show(node("D-1")).assertIsDisplayed();node("이번 주 기록").assertDoesNotExist();node("기록 보기").assertDoesNotExist()
        node("병원 일정").assertExists();node("다음 병원 일정").assertDoesNotExist()
        node("병원 일정 관리").assertDoesNotExist()
        ui.onNodeWithContentDescription("신장내과 09:30 진료 예약").assertExists()
        ui.onNodeWithContentDescription("내분비내과 11:20 진료 예약").assertExists()
        show(ui.onNodeWithContentDescription("병원 일정 추가")).performClick()
        node("예약일 선택해 주세요").assertExists()
        ui.onNode(hasSetTextAction() and hasText("예약시간 · HH:mm")).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        back();assertEquals(1,snapshot().appointments.size)
        click("연락처 등록")
        click("이모지 선택");show(ui.onNodeWithContentDescription("병원 이모지")).performClick()
        input("연락처 이름","테스트 연락처");input("전화번호","010-0000-0000");click("저장")
        await{snapshot().contacts.size==1}
        assertEquals("🏥",snapshot().contacts.single().emoji)
        node("연락처 모음").assertDoesNotExist();node("연락처 관리").assertDoesNotExist()
        show(avatar("테스트 연락처")).performClick()
        show(ui.onNodeWithContentDescription("테스트 연락처 전화")).assertIsDisplayed()
        show(ui.onNodeWithContentDescription("테스트 연락처 문자")).assertIsDisplayed();closeContact()
        ui.activityRule.scenario.recreate()
        await{ui.onAllNodesWithText("테스트 연락처").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("검사 후 투석실 방문",snapshot().appointments.single().memo)
        assertEquals(saved.departmentTimes,snapshot().appointments.single().departmentTimes)
    }
    @Test fun appointmentInputSurvivesNestedRegistrationAndActivityRecreation() {
        click("병원 일정 등록");pickDate(LocalDate.now());input("예약시간 · HH:mm","16:40");input("메모","작성 중 메모")
        click("진료과 등록·관리");click("+ 진료과 등록");input("진료과 이름","신장내과");click("저장")
        await{snapshot().departments.size==1};back()
        ui.onNode(hasSetTextAction() and hasText("16:40")).assertExists()
        ui.onNode(hasSetTextAction() and hasText("작성 중 메모")).assertExists()
        click("신장내과");ui.activityRule.scenario.recreate()
        await{ui.onAllNodes(hasSetTextAction() and hasText("작성 중 메모")).fetchSemanticsNodes().isNotEmpty()}
        click("저장");await{snapshot().appointments.size==1}
        assertEquals("신장내과",snapshot().appointments.single().departments.single().name)
        assertEquals("16:40",snapshot().appointments.single().time)
        assertEquals("16:40",snapshot().appointments.single().departmentTimes.values.single())
        assertEquals("작성 중 메모",snapshot().appointments.single().memo)
    }
    @Test fun repeatEditDeleteAndCatalogChangesPreserveOriginalAppointment() {
        val d=Department(id="dept",name="신장내과",color=0xFF47956E)
        val c=CareTemplate(id="care",name="주사",iconKey="injection")
        val a=Appointment(id="original",date=LocalDate.now().plusDays(2).toString(),time="09:00",departments=listOf(d),careItems=listOf(c.asCareTask()),memo="원본 메모")
        runBlocking{app.repository.department(d);app.repository.careTemplate(c);app.repository.appointment(a)}
        await{ui.onAllNodesWithText("D-2").fetchSemanticsNodes().isNotEmpty()}
        settings();click("진료과");show(ui.onNodeWithContentDescription("신장내과 수정")).performClick()
        input("진료과 이름","수정한 진료과");click("저장");await{snapshot().departments.single().name=="수정한 진료과"}
        show(ui.onNodeWithContentDescription("수정한 진료과 삭제")).performClick();click("확인")
        await{snapshot().departments.isEmpty()};back();click("검사·치료 항목")
        show(ui.onNodeWithContentDescription("주사 수정")).performClick();input("치료 항목 이름","주사 수정")
        click("아이콘 · 주사");show(ui.onNodeWithContentDescription("약 아이콘")).performClick();click("저장")
        await{snapshot().careTemplates.single().iconKey=="medicine"}
        show(ui.onNodeWithContentDescription("주사 수정 삭제")).performClick();click("확인")
        await{snapshot().careTemplates.isEmpty()};back();back()
        assertEquals(a,snapshot().appointments.single())
        settings();click("병원 일정");click("같은 구성으로 다음 예약")
        ui.onNode(hasSetTextAction() and hasText("신장내과 예약시간 · HH:mm")).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        ui.onNode(hasSetTextAction() and hasText("메모")).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        node("예약일 선택해 주세요").assertExists()
        pickDate(LocalDate.now().plusDays(3));input("신장내과 예약시간 · HH:mm","14:20");input("메모","다음 예약 메모")
        click("저장");await{snapshot().appointments.size==2}
        val next=snapshot().appointments.single{it.id!=a.id}
        assertEquals(a.departments,next.departments);assertEquals(a.careItems,next.careItems)
        assertEquals("14:20",next.departmentTime(d))
        show(ui.onNodeWithContentDescription("${next.id} 일정 수정")).performClick();input("메모","수정 메모");click("저장")
        await{snapshot().appointments.any{it.memo=="수정 메모"}}
        show(ui.onNodeWithContentDescription("${next.id} 일정 삭제")).performClick();click("확인")
        await{snapshot().appointments.size==1};assertEquals(a,snapshot().appointments.single())
    }
    @Test fun pastScheduleIsRetainedAndManualRecordingUsesRecordsMenu() {
        val old=LocalDateTime.now().minusMinutes(2)
        val future=LocalDate.now().plusDays(2)
        runBlocking {
            app.repository.appointment(Appointment(id="past",date=old.toLocalDate().toString(),time=old.toLocalTime().withSecond(0).withNano(0).toString()))
            app.repository.appointment(Appointment(id="future",date=future.toString(),time="10:00"))
        }
        await{ui.onAllNodesWithText("D-2").fetchSemanticsNodes().isNotEmpty()}
        settings();click("병원 일정");show(node("지난 일정")).assertIsDisplayed();assertEquals(2,snapshot().appointments.size);back();back()
        ui.onNodeWithText("기록",useUnmergedTree=true).performClick();ui.onNodeWithContentDescription("기록 추가").performClick();click("추가투석 기록 추가")
        await{ui.onAllNodesWithText("이번 기록만 수량 조정").fetchSemanticsNodes().isNotEmpty()}
        node("배액 기록 · 선택").assertExists();assertTrue(snapshot().treatments.isEmpty())
    }
    @Test fun contactEditSurvivesRecreationAndDeleteRemovesHomeActions() {
        runBlocking{app.repository.preferences(Preferences(darkMode="DARK",celebrate=false))}
        settings();click("연락처");click("+ 연락처 등록")
        input("연락처 이름","테스트 연락처");input("전화번호","010-0000-0000");click("저장")
        await{snapshot().contacts.size==1}
        contactMenu("테스트 연락처","수정")
        input("연락처 이름","수정한 연락처");input("전화번호","02-000-0000")
        click("문자 허용");click("이모지 선택");show(ui.onNodeWithContentDescription("청진기 이모지")).performClick()
        ui.activityRule.scenario.recreate()
        await{ui.onAllNodes(hasSetTextAction() and hasText("수정한 연락처")).fetchSemanticsNodes().isNotEmpty()}
        click("저장");await{snapshot().contacts.single().name=="수정한 연락처"}
        assertFalse(snapshot().contacts.single().allowSms);assertEquals("🩺",snapshot().contacts.single().emoji)
        back();back();show(avatar("수정한 연락처")).performClick();show(node("02-000-0000")).assertIsDisplayed()
        ui.onNodeWithContentDescription("수정한 연락처 전화").assertExists();ui.onNodeWithContentDescription("수정한 연락처 문자").assertDoesNotExist();closeContact()
        settings();click("연락처");contactMenu("수정한 연락처","삭제");click("확인")
        await{snapshot().contacts.isEmpty()};back();back()
        node("수정한 연락처").assertDoesNotExist();ui.onNodeWithContentDescription("수정한 연락처 전화").assertDoesNotExist()
        show(node("연락처 등록")).assertIsDisplayed()
    }
    @Test fun contactPhoneAcceptsSpacesHyphensAndShortNumbers() {
        settings();click("연락처");click("+ 연락처 등록")
        input("연락처 이름","번호 입력 검사")
        listOf("15771111","1577 1111","1577-1111").forEach { value ->
            input("전화번호",value)
            ui.onNode(hasSetTextAction() and hasText("전화번호")).assertTextContains("1577-1111")
            node("저장").assertIsEnabled()
        }
        ui.activityRule.scenario.recreate()
        await{ui.onAllNodes(hasSetTextAction() and hasText("번호 입력 검사")).fetchSemanticsNodes().isNotEmpty()}
        ui.onNode(hasSetTextAction() and hasText("전화번호")).assertTextContains("1577-1111")
        click("저장");await{snapshot().contacts.size==1}
        assertEquals("1577-1111",snapshot().contacts.single().phone)
        contactMenu("번호 입력 검사","수정")
        back() // Merely opening an already formatted number must not mark the editor dirty.
        ui.onNodeWithContentDescription("번호 입력 검사 더보기").assertExists()
        contactMenu("번호 입력 검사","수정")
        listOf("112","119","114").forEach { value ->
            input("전화번호",value)
            ui.onNode(hasSetTextAction() and hasText("전화번호")).assertTextContains(value)
            node("저장").assertIsEnabled()
        }
        click("저장");await{snapshot().contacts.single().phone=="114"}
        contactMenu("번호 입력 검사","수정")
        ui.onNode(hasSetTextAction() and hasText("전화번호")).assertTextContains("114")
    }
    @OptIn(ExperimentalTestApi::class)
    @Test fun contactPhoneMiddleEditingAndBackspaceKeepDigitsAndCursor() {
        settings();click("연락처");click("+ 연락처 등록")
        input("연락처 이름","커서 검사");input("전화번호","01012345678")
        val phone=ui.onNode(hasSetTextAction() and hasText("전화번호"))
        phone.assertTextContains("010-1234-5678")
        // Select original digits 4–7, paste formatted text, then insert at that same position.
        phone.performTextInputSelection(TextRange(3,7),relativeToOriginalText=true)
        phone.performTextInput("9876 ")
        phone.assertTextContains("010-9876-5678")
        phone.performTextInputSelection(TextRange(3),relativeToOriginalText=true)
        phone.performKeyInput { pressKey(Key.Backspace) }
        phone.performTextInput("0")
        phone.assertTextContains("010-9876-5678")
        phone.performTextInputSelection(TextRange(11),relativeToOriginalText=true)
        phone.performKeyInput { pressKey(Key.Backspace) }
        phone.performTextInput("9")
        phone.assertTextContains("010-9876-5679")
        ui.runOnIdle{(ui.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0)}
        click("저장");await{snapshot().contacts.size==1}
        assertEquals("010-9876-5679",snapshot().contacts.single().phone)
    }
    @Test fun consecutiveContactRegistrationsStartEmptyAndKeepExistingContacts() {
        settings();click("연락처")
        val saved=mutableListOf<Contact>()
        repeat(3) { index ->
            click("+ 연락처 등록")
            listOf("연락처 이름","전화번호").forEach { label ->
                ui.onNode(hasSetTextAction() and hasText(label)).assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
            }
            input("연락처 이름","연락처 ${index+1}");input("전화번호","010-0000-000${index+1}")
            if(index==0) {
                click("문자 허용");click("이모지 선택")
                show(ui.onNodeWithContentDescription("병원 이모지")).performClick()
            }
            click("저장");await{snapshot().contacts.size==index+1}
            saved.forEach { previous -> assertEquals(previous,snapshot().contacts.single{it.id==previous.id}) }
            val contact=snapshot().contacts.single{it.name=="연락처 ${index+1}"}
            if(index>0) {assertEquals("👤",contact.emoji);assertTrue(contact.allowCall);assertTrue(contact.allowSms)}
            saved+=contact
        }
        assertEquals(3,saved.map{it.id}.distinct().size)
    }
    @Test fun contactEditCancelAndNewDraftRecreationKeepIndependentState() {
        val original=Contact(id="existing-contact",name="기존 연락처",phone="02-000-0000",emoji="🏥",allowSms=false)
        runBlocking{app.repository.createContact(original)}
        settings();click("연락처")
        contactMenu("기존 연락처","수정")
        input("연락처 이름","수정한 기존 연락처");click("저장")
        await{snapshot().contacts.single().name=="수정한 기존 연락처"}
        val edited=snapshot().contacts.single()
        click("+ 연락처 등록");input("연락처 이름","취소할 입력");input("전화번호","010-1111-1111")
        click("전화 허용");back();click("확인")
        click("+ 연락처 등록")
        listOf("연락처 이름","전화번호").forEach { label ->
            ui.onNode(hasSetTextAction() and hasText(label)).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        }
        input("연락처 이름","새 연락처");input("전화번호","잘못된 번호");node("저장").assertIsNotEnabled()
        input("전화번호","010-2222-2222")
        ui.activityRule.scenario.recreate()
        await{ui.onAllNodes(hasSetTextAction() and hasText("새 연락처")).fetchSemanticsNodes().isNotEmpty()}
        click("저장");await{snapshot().contacts.size==2}
        assertEquals(edited,snapshot().contacts.single{it.id==original.id})
        val added=snapshot().contacts.single{it.id!=original.id}
        assertEquals("새 연락처",added.name);assertEquals("010-2222-2222",added.phone)
        assertEquals("👤",added.emoji);assertTrue(added.allowCall);assertTrue(added.allowSms)
        contactMenu("새 연락처","수정")
        input("연락처 이름","버릴 수정");back();click("확인")
        assertEquals(added,snapshot().contacts.single{it.id==added.id})
        contactMenu("새 연락처","삭제");click("확인")
        await{snapshot().contacts.size==1};assertEquals(edited,snapshot().contacts.single())
    }
    @Test fun newDataFullFileBackupRestoresAfterReset()=runBlocking {
        val d=Department(name="테스트 진료과")
        val c=CareTemplate(name="검사",iconKey="lab")
        val eye=Department(name="테스트 안과",color=0xFF47956E)
        val a=Appointment(date=LocalDate.now().plusDays(1).toString(),time="09:30",departments=listOf(d,eye),careItems=listOf(c.asCareTask()),
            departmentTimes=mapOf(d.id to "09:30",eye.id to "11:20"))
        val contact=Contact(name="테스트 연락처",phone="010-0000-0000",emoji="🏥",allowSms=false)
        app.repository.department(d);app.repository.careTemplate(c);app.repository.appointment(a);app.repository.createContact(contact)
        val file=File(app.cacheDir,"appointment-roundtrip.json")
        app.backup.export(Uri.fromFile(file));val saved=app.backup.read(Uri.fromFile(file))
        app.repository.restore(Snapshot());app.backup.restore(saved)
        val restored=app.repository.snapshot()
        assertEquals(listOf(d),restored.departments);assertEquals(listOf(c),restored.careTemplates)
        assertEquals(listOf(a),restored.appointments);assertEquals(listOf(contact),restored.contacts)
    }
    @Test fun timeSelectionDialogAppliesTimeAndCanSaveWithoutCategories() {
        click("병원 일정 등록");pickDate(LocalDate.now().plusDays(1));click("시간 선택")
        node("예약시간").assertExists();click("확인")
        ui.onNode(hasSetTextAction() and hasText("예약시간 · HH:mm")).assertTextContains("09:00")
        click("저장");await{snapshot().appointments.size==1}
        val saved=snapshot().appointments.single()
        assertEquals("09:00",saved.time);assertTrue(saved.departments.isEmpty());assertNull(saved.care)
    }
    @Test fun threeAvatarsShareRowAndModalRespectsEachContactPermission() {
        val contacts=listOf(
            Contact(id="a",name="연락 A",phone="010-0000-0001",createdAt=1,emoji="🏥",allowCall=false),
            Contact(id="b",name="연락 B",phone="010-0000-0002",createdAt=2,emoji="🩺",allowSms=false),
            Contact(id="c",name="연락 C",phone="010-0000-0003",createdAt=3,emoji="💊"),
            Contact(id="d",name="연락 D",phone="010-0000-0004",createdAt=4,emoji="👤",allowCall=false,allowSms=false))
        runBlocking{contacts.forEach{app.repository.createContact(it)}}
        await{ui.onAllNodesWithContentDescription("연락 D 연락처").fetchSemanticsNodes().isNotEmpty()}
        show(avatar("연락 A"))
        val bounds=contacts.map{avatar(it.name).getUnclippedBoundsInRoot()}
        assertEquals(bounds[0].top.value,bounds[1].top.value,.5f)
        assertEquals(bounds[0].top.value,bounds[2].top.value,.5f)
        assertTrue(bounds[0].right<=bounds[1].left && bounds[1].right<=bounds[2].left)
        assertTrue(bounds[3].top>=bounds[0].bottom)
        node("연락처 관리").assertDoesNotExist()
        contacts.forEach { c ->
            show(avatar(c.name)).performClick();node("연결 방법 선택").assertExists()
            if(c.allowCall)ui.onNodeWithContentDescription("${c.name} 전화").assertExists()else ui.onNodeWithContentDescription("${c.name} 전화").assertDoesNotExist()
            if(c.allowSms)ui.onNodeWithContentDescription("${c.name} 문자").assertExists()else ui.onNodeWithContentDescription("${c.name} 문자").assertDoesNotExist()
            if(!c.allowCall && !c.allowSms)node("이 연락처의 전화·문자 연결이 꺼져 있어요.").assertExists()
            closeContact()
        }
    }
}
