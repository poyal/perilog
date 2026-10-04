# Room and kotlinx.serialization supply their own consumer keep rules.
-keepattributes SourceFile,LineNumberTable

# Glance persists widget class names. Merging these two providers makes their
# receiver-to-provider mappings identical and can render records in appointment IDs.
-keep class com.poyal.perilog.widget.DailyRecordWidget { *; }
-keep class com.poyal.perilog.widget.AppointmentWidget { *; }
