package com.poyal.perilog.capture

import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.BuildConfig
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.visibleRecords
import com.poyal.perilog.domain.complete
import com.poyal.perilog.domain.inventory
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/** Manual emulator setup, compiled only with -PcaptureScreenshots. Keeps existing edits. */
@RunWith(AndroidJUnit4::class)
class PreviewSamples {
    /** Opt-in memo examples for manual review. Repeated runs keep edits and all drafts. */
    @Test fun loadMemos() = runBlocking {
        require(BuildConfig.DEBUG && Build.HARDWARE in listOf("ranchu","goldfish")) {
            "메모 샘플은 개발용 에뮬레이터에서만 준비합니다."
        }
        val app=ApplicationProvider.getApplicationContext<PerilogApplication>()
        val before=app.repository.snapshot()
        app.backup.protect()
        val day=LocalDate.now().toString()
        val now=System.currentTimeMillis()
        fun longMemo(title:String)=listOf("$title · 메모 테스트", "첫 번째 확인 내용을 적어두었어요.",
            "두 번째 내용은 줄을 바꾸어 남겼어요.", "긴 문장도 카드 너비에 맞춰 자연스럽게 이어지는지 확인하는 예시입니다.",
            "마지막 줄까지 보려면 더 보기를 눌러 주세요.").joinToString("\n")
        val sampleProduct=Product(id="preview-memo-supply",name="메모 테스트용 소모품",kind="소모품",
            color=0xFF8772B5,vendor="샘플",memo=longMemo("물품 안내"))
        val product=before.products.find{it.id==sampleProduct.id} ?: sampleProduct
        val record=Treatment(id="preview-memo-record",date=day,kind="MANUAL",basisMl=null,
            items=listOf(Item(product.id,product.name,1)),saved=true,usageConfirmed=true,
            memo=longMemo("추가투석 기록"),createdAt=now-120000)
        val addedRecords=if(before.visibleRecords().any{it.id==record.id} || before.usages.any{it.id==record.id})emptyList()else listOf(record)
        val receipt=Receipt(id="preview-memo-receipt",date=day,createdAt=now-300000,
            lines=listOf(ReceiptLine(id="preview-memo-lot",productId=product.id,quantity=10)),
            memo="입고 메모 테스트\n상자 안의 수량과 품목을 확인했어요.\n거래명세서는 별도로 보관했어요.")
        val adjustments=listOf(
            StockAdjustment(id="preview-memo-add",productId=product.id,date=day,delta=2,createdAt=now-240000,
                memo="수량 추가 메모 테스트 · 빠뜨린 샘플 2개를 추가했어요."),
            StockAdjustment(id="preview-memo-loss",productId=product.id,date=day,delta=-1,createdAt=now-180000,
                memo=longMemo("수량 차감 사유")))
        val count=StockCount(id="preview-memo-count",productId=product.id,date=day,quantity=10,createdAt=now-60000,
            memo=longMemo("수량 확인 이력"))
        val department=before.departments.find{it.id=="preview-memo-department"}
            ?: Department(id="preview-memo-department",name="샘플 진료과",color=0xFF8772B5)
        val appointmentAt=LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
        val appointment=Appointment(id="preview-memo-appointment",date=appointmentAt.toLocalDate().toString(),
            time=appointmentAt.toLocalTime().toString(),departments=listOf(department),memo=longMemo("병원 일정"))
        val products=before.products.map { p ->
            if(p.id.startsWith("preview-") && p.memo.isBlank())p.copy(memo=if(p.id=="preview-d15")longMemo("물품 안내")
                else "물품 메모 테스트 · ${p.name}의 이름과 포장을 확인했어요.")else p
        }
        val records=before.treatments.map { t ->
            if(t.id.startsWith("preview-") && t.memo.isBlank() && before.drafts.none{it.id==t.id})
                t.copy(memo=longMemo("투석 기록"))else t
        }
        val next=before.copy(
            products=(products+product).distinctBy{it.id},
            treatments=records+addedRecords,
            usages=before.usages+addedRecords.map{Usage(it.id,it.date,it.items,it.kind,it.createdAt)},
            receipts=(before.receipts+receipt).distinctBy{it.id},
            adjustments=(before.adjustments+adjustments).distinctBy{it.id},
            counts=(before.counts+count).distinctBy{it.id},
            departments=(before.departments+department).distinctBy{it.id},
            appointments=(before.appointments+appointment).distinctBy{it.id})
        val previousStock=inventory(before).products
        val nextStock=inventory(next).products
        check(previousStock.all{(id,stock)->nextStock[id]==stock}) { "기존 물품 재고가 바뀌면 메모 샘플을 적용하지 않습니다." }
        app.repository.restore(next)
        val after=app.repository.snapshot()
        check(before.drafts.toSet()==after.drafts.toSet())
        check(before.contacts.toSet()==after.contacts.toSet())
        check(before.preferences==after.preferences)
        check(before.receipts.all{it in after.receipts} && before.adjustments.all{it in after.adjustments} && before.counts.all{it in after.counts})
        check(before.appointments.all{it in after.appointments})
        check(before.products.all{old->after.products.any{it.id==old.id && it.copy(memo=old.memo)==old && (old.memo.isBlank() || it.memo==old.memo)}})
        check(before.treatments.all{old->after.treatments.any{it.id==old.id && it.copy(memo=old.memo)==old && (old.memo.isBlank() || it.memo==old.memo)}})
        InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply{
            putString("memo_samples","투석 기록·병원 일정·물품·입고·재고 추가·차감·수량 확인 이력")
            putString("preserved","기존 메모·입력값·초안·기존 물품 재고 유지")
            putString("memo_counts","물품 ${after.products.count{it.memo.isNotBlank()}}개 · 기록 ${after.treatments.count{it.memo.isNotBlank()}}건 · 병원 일정 ${after.appointments.count{it.memo.isNotBlank()}}건")
        })
    }

    @Test fun load() = runBlocking {
        require(BuildConfig.DEBUG && Build.HARDWARE in listOf("ranchu","goldfish")) {
            "샘플 데이터는 개발용 에뮬레이터에서만 준비합니다."
        }
        val app=ApplicationProvider.getApplicationContext<PerilogApplication>()
        val before=app.repository.snapshot()
        app.backup.protect()
        val date=LocalDate.now()
        val products=listOf(
            Product(id="preview-d15",name="투석액 1.5%",color=0xFF2167B8),
            Product(id="preview-d25",name="투석액 2.5%",color=0xFF47956E),
            Product(id="preview-d425",name="투석액 4.25%",color=0xFFEF8752),
            Product(id="preview-d75",name="투석액 7.5%",color=0xFF30343B),
            Product(id="preview-cassette",name="카세트",kind="소모품",color=0xFF647789),
            Product(id="preview-line",name="손투석 라인",kind="소모품",color=0xFF8772B5))
        val composition=listOf(0,1,4).map{Item(products[it].id,products[it].name,1)}
        val manualItems=listOf(Item(products[0].id,products[0].name,1),Item(products[5].id,products[5].name,1))
        val history=(1..14).map{n->
            val day=date.minusDays(n.toLong()).toString()
            Treatment(id="preview-machine-$day",date=day,weightGrams=62000+(n%5)*100,
                systolic=118+n%8,diastolic=76+n%5,initialDrain=2180+n*10,machineUf=460+n*12,
                dwellMinutes=110,items=composition,usageConfirmed=true,saved=true,memo="샘플 기록",createdAt=100L+n,
                usageTemplateId="preview-night",usageTemplateName="밤 투석 · 1.5 + 2.5",usageTemplateColor=0xFF2167B8)
        }
        val current=Treatment(id="preview-machine-$date",date=date.toString(),weightGrams=62300,
            systolic=120,diastolic=80,items=composition,usageConfirmed=true,saved=true,createdAt=1000L,
            usageTemplateId="preview-night",usageTemplateName="밤 투석 · 1.5 + 2.5",usageTemplateColor=0xFF2167B8)
        val manualDate=date.minusDays(3).toString()
        val manual=Treatment(id="preview-manual-$manualDate",date=manualDate,kind="MANUAL",
            manualDrain=2195,drainUnit="kg",basisMl=null,items=manualItems,usageConfirmed=true,
            saved=true,memo="샘플 추가투석",createdAt=500L,
            usageTemplateId="preview-manual",usageTemplateName="추가투석 · 1.5 + 라인",usageTemplateColor=0xFF8772B5)
        val existing=before.visibleRecords()
        val yesterday=date.minusDays(1).toString()
        val draft=Treatment(id="preview-yesterday-draft-$date",date=yesterday,
            weightGrams=62200,systolic=121,diastolic=78,memo="샘플 미입력 기록",createdAt=900L)
        val addedDrafts=if(existing.none{it.id==draft.id || it.date==yesterday && !it.complete()} && before.usages.none{it.id==draft.id})listOf(Draft(draft.id,draft))else emptyList()
        val added=(history+current+manual).filter{sample->
            existing.none{it.id==sample.id || it.date==sample.date && it.kind==sample.kind} &&
                before.usages.none{it.id==sample.id}
        }
        val receipt=Receipt(id="preview-delivery",date=date.minusDays(20).toString(),createdAt=1,
            lines=products.map{ReceiptLine(id="preview-lot-${it.id}",productId=it.id,
                quantity=if(it.kind=="투석액")30 else 40)},memo="샘플 정기 입고")
        val templates=listOf(
            UsageTemplate(id="preview-night",name="밤 투석 · 1.5 + 2.5",items=composition),
            UsageTemplate(id="preview-manual",name="추가투석 · 1.5 + 라인",items=manualItems,color=0xFF8772B5))
        app.repository.restore(before.copy(
            products=(before.products+products).distinctBy{it.id},
            templates=(before.templates+templates).distinctBy{it.id},
            treatments=before.treatments+added,
            drafts=before.drafts+addedDrafts,
            usages=before.usages+added.map{Usage(it.id,it.date,it.items,it.kind,it.createdAt)},
            receipts=(before.receipts+receipt).distinctBy{it.id}))
        val after=app.repository.snapshot()
        check(products.all{sample->after.products.any{it.id==sample.id}})
        check(templates.all{sample->after.templates.any{it.id==sample.id}})
        InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply{
            putString("sample_setup","품목 ${after.products.size}개 · 구성 ${after.templates.size}개 · 기록 ${after.treatments.size}건")
            putString("added_records",added.size.toString())
            putString("added_drafts",addedDrafts.size.toString())
        })
    }
}
