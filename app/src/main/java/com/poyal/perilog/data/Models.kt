package com.poyal.perilog.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

val codec = Json { encodeDefaults = true; ignoreUnknownKeys = false }
fun newId(): String = UUID.randomUUID().toString()
fun today(): String = LocalDate.now().toString()

@Serializable
data class Item(val productId: String, val name: String, val quantity: Int, val batchId: String? = null)

@Entity(tableName = "products") @Serializable
data class Product(@PrimaryKey val id: String = newId(), val name: String, val kind: String = "투석액",
    val color: Long = 0xFF2167B8, val active: Boolean = true, val lowStock: Int? = null,
    val vendor: String = "벤티브", val memo: String = "")

@Entity(tableName = "templates") @Serializable
data class UsageTemplate(@PrimaryKey val id: String = newId(), val name: String, val items: List<Item>,
    @ColumnInfo(defaultValue="4280379320") val color: Long = 0xFF2167B8)

@Entity(tableName = "treatments") @Serializable
data class Treatment(@PrimaryKey val id: String = newId(), val date: String = today(), val kind: String = "MACHINE",
    val weightGrams: Int? = null, val systolic: Int? = null, val diastolic: Int? = null,
    val initialDrain: Int? = null, val machineUf: Int? = null, val basisMl: Int? = 2000,
    val dwellMinutes: Int? = null, val manualDrain: Int? = null, val drainUnit: String = "mL",
    val previousFill: Int? = null, val items: List<Item> = emptyList(), val usageConfirmed: Boolean = false,
    val saved: Boolean = false, val memo: String = "", val sourceDate: String? = null,
    val startTime: String = "", val endTime: String = "", val interrupted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val usageTemplateId: String? = null, val usageTemplateName: String? = null, val usageTemplateColor: Long? = null)

@Entity(tableName = "usages") @Serializable
data class Usage(@PrimaryKey val id: String, val date: String, val items: List<Item>, val kind: String,
    val createdAt: Long, val cancelled: Boolean = false)

@Serializable
// Kept for old backups; expiry no longer affects stock or appears in the app.
data class ReceiptLine(val id: String = newId(), val productId: String, val quantity: Int, val expiry: String? = null)

@Entity(tableName = "receipts") @Serializable
data class Receipt(@PrimaryKey val id: String = newId(), val date: String = today(), val lines: List<ReceiptLine>,
    val memo: String = "", val createdAt: Long = System.currentTimeMillis(), val cancelled: Boolean = false,
    val requestPlanId: String? = null)

@Entity(tableName = "counts") @Serializable
data class StockCount(@PrimaryKey val id: String = newId(), val productId: String, val date: String = today(),
    val quantity: Int, val memo: String = "현재 재고 확인", val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "adjustments") @Serializable
data class StockAdjustment(@PrimaryKey val id: String = newId(), val productId: String, val date: String = today(),
    val delta: Int, val memo: String, val createdAt: Long = System.currentTimeMillis(), val cancelled: Boolean = false)

@Entity(tableName = "audit") @Serializable
data class Audit(@PrimaryKey val id: String = newId(), val at: Long = System.currentTimeMillis(),
    val action: String, val targetId: String, val before: String, val after: String)

@Entity(tableName = "drafts") @Serializable
data class Draft(@PrimaryKey val id: String, val treatment: Treatment)

@Entity(tableName = "departments") @Serializable
data class Department(@PrimaryKey val id: String = newId(), val name: String,
    val color: Long = 0xFF2167B8)

@Serializable
data class CareTask(val id: String = newId(), val name: String = "", val iconKey: String = "medical")

@Entity(tableName = "care_templates") @Serializable
data class CareTemplate(@PrimaryKey val id: String = newId(), val name: String,
    // Kept to decode and migrate backups made before individual care items.
    val tasks: List<CareTask> = emptyList(), @ColumnInfo(defaultValue="'medical'") val iconKey: String = "medical")

@Entity(tableName = "appointments") @Serializable
data class Appointment(@PrimaryKey val id: String = newId(), val date: String = "", val time: String = "",
    val departments: List<Department> = emptyList(), val care: CareTemplate? = null, val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue="'[]'") val careItems: List<CareTask> = emptyList(),
    @ColumnInfo(defaultValue="'{}'") val departmentTimes: Map<String,String> = emptyMap())

@Entity(tableName = "contacts") @Serializable
data class Contact(@PrimaryKey val id: String = newId(), val name: String, val phone: String = "",
    val createdAt: Long = System.currentTimeMillis(), @ColumnInfo(defaultValue="'👤'") val emoji: String = "👤",
    @ColumnInfo(defaultValue="1") val allowCall: Boolean = true, @ColumnInfo(defaultValue="1") val allowSms: Boolean = true)

@Entity(tableName = "settings")
data class SettingsRow(@PrimaryKey val id: Int = 1, val payload: String)

@Serializable
data class Basis(val from: String, val ml: Int)

@Serializable
data class Preferences(val basis: List<Basis> = listOf(Basis("1970-01-01", 2000)),
    val expiryDays: Int = 7, val backupDays: Int = 1, val keepBackups: Int = 30,
    val darkMode: String = "SYSTEM", val celebrate: Boolean = true, val lock: Boolean = false,
    val reminder: Boolean = false, val reminderHour: Int = 21, val reminderMinute: Int = 0,
    val palette: List<Long> = listOf(0xFF2167B8,0xFF47956E,0xFFEF8752,0xFF30343B,0xFFBA668B,0xFF8772B5,0xFFDAAB36,0xFF5B9FA6),
    val celebratedDates: Set<String> = emptySet(), val lastDrainUnit: String = "g",
    val contactOrder: List<String> = emptyList())

@Serializable
data class Snapshot(val formatVersion: Int = 1, val appVersion: String = "1.0.1",
    val exportedAt: Long = System.currentTimeMillis(), val products: List<Product> = emptyList(),
    val templates: List<UsageTemplate> = emptyList(), val treatments: List<Treatment> = emptyList(),
    val usages: List<Usage> = emptyList(), val receipts: List<Receipt> = emptyList(),
    val counts: List<StockCount> = emptyList(), val audit: List<Audit> = emptyList(), val drafts: List<Draft> = emptyList(),
    val preferences: Preferences = Preferences(), val departments: List<Department> = emptyList(),
    val careTemplates: List<CareTemplate> = emptyList(), val appointments: List<Appointment> = emptyList(),
    val contacts: List<Contact> = emptyList(), val adjustments: List<StockAdjustment> = emptyList(),
    val replenishmentPlans: List<ReplenishmentPlan> = emptyList())
