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
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Manual emulator setup, compiled only with -PcaptureScreenshots. Keeps existing edits. */
@RunWith(AndroidJUnit4::class)
class PreviewSamples {
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
