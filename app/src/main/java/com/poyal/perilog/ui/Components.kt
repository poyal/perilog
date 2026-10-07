@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.poyal.perilog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.poyal.perilog.R
import com.poyal.perilog.data.*
import kotlinx.serialization.encodeToString
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.math.BigDecimal

val LocalInputErrors=staticCompositionLocalOf<MutableMap<String,String>>{mutableMapOf()}
val Coral=Color(0xFFF47761)
@Composable fun CompletionBadge(done:Boolean) {
    Surface(shape=CircleShape,color=if(done)MaterialTheme.colorScheme.primary else Color.Transparent,
        border=if(done)null else BorderStroke(1.5.dp,Coral),modifier=Modifier.size(32.dp).semantics {
            stateDescription=if(done)"완료"else"미입력"
        }) {
        Box(contentAlignment=Alignment.Center) {
            if(done)Icon(Icons.Outlined.Check,null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.onPrimary)
        }
    }
}
internal val light=lightColorScheme(
    primary=Color(0xFF2476CF),onPrimary=Color.White,primaryContainer=Color(0xFFDDEFFF),onPrimaryContainer=Color(0xFF12345A),
    secondary=Color(0xFFB34E3B),onSecondary=Color.White,secondaryContainer=Color(0xFFFFEAE3),onSecondaryContainer=Color(0xFF873B2E),
    tertiary=Color(0xFF337D77),onTertiary=Color.White,tertiaryContainer=Color(0xFFDEF2EE),onTertiaryContainer=Color(0xFF205951),
    background=Color(0xFFE7F5FF),onBackground=Color(0xFF12345A),surface=Color(0xFFFFFCF7),onSurface=Color(0xFF12345A),
    surfaceVariant=Color(0xFFEAF2F8),onSurfaceVariant=Color(0xFF526B86),surfaceTint=Color(0xFF2476CF),
    surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFFFFCF7),surfaceContainer=Color(0xFFF4F8FC),
    surfaceContainerHigh=Color(0xFFEAF2F8),surfaceContainerHighest=Color(0xFFDDEAF5),
    outline=Color(0xFF8198AF),outlineVariant=Color(0xFFD7E4EF),inverseSurface=Color(0xFF203A54),inverseOnSurface=Color.White,inversePrimary=Color(0xFFA9D3FF),
    error=Color(0xFFAC3F33),onError=Color.White,errorContainer=Color(0xFFFFE5DF),onErrorContainer=Color(0xFF792B22))
internal val dark=darkColorScheme(
    primary=Color(0xFFA9D3FF),onPrimary=Color(0xFF103252),primaryContainer=Color(0xFF294F72),onPrimaryContainer=Color(0xFFE3F0FF),
    secondary=Color(0xFFFFB7A7),onSecondary=Color(0xFF5E2A21),secondaryContainer=Color(0xFF573C37),onSecondaryContainer=Color(0xFFFFDBD1),
    tertiary=Color(0xFFA3DAD0),onTertiary=Color(0xFF153F38),tertiaryContainer=Color(0xFF285C53),onTertiaryContainer=Color(0xFFD6F5EE),
    background=Color(0xFF101D2C),onBackground=Color(0xFFF2F5FA),surface=Color(0xFF203449),onSurface=Color(0xFFF2F5FA),
    surfaceVariant=Color(0xFF30485F),onSurfaceVariant=Color(0xFFC1D0E0),surfaceTint=Color(0xFFA9D3FF),
    surfaceContainerLowest=Color(0xFF152639),surfaceContainerLow=Color(0xFF203449),surfaceContainer=Color(0xFF283F56),
    surfaceContainerHigh=Color(0xFF30485F),surfaceContainerHighest=Color(0xFF3A526A),
    outline=Color(0xFF819BB7),outlineVariant=Color(0xFF49627A),inverseSurface=Color(0xFFE8F2FA),inverseOnSurface=Color(0xFF203142),inversePrimary=Color(0xFF2476CF),
    error=Color(0xFFFFB7A7),onError=Color(0xFF601E17),errorContainer=Color(0xFF773229),onErrorContainer=Color(0xFFFFDAD2))

@Composable fun PerilogTheme(mode:String,content:@Composable ()->Unit) {
    val night=mode=="DARK" || mode=="SYSTEM" && isSystemInDarkTheme()
    val defaults=Typography()
    val type=defaults.copy(
        headlineLarge=defaults.headlineLarge.copy(fontSize=34.sp,fontWeight=FontWeight.Bold),
        headlineMedium=defaults.headlineMedium.copy(fontSize=28.sp,fontWeight=FontWeight.Bold),
        headlineSmall=defaults.headlineSmall.copy(fontSize=24.sp,fontWeight=FontWeight.Bold),
        titleLarge=defaults.titleLarge.copy(fontSize=22.sp,fontWeight=FontWeight.Bold),
        titleMedium=defaults.titleMedium.copy(fontSize=18.sp,fontWeight=FontWeight.Bold),
        bodyLarge=defaults.bodyLarge.copy(fontSize=16.sp),bodyMedium=defaults.bodyMedium.copy(fontSize=14.sp,lineHeight=21.sp),
        labelLarge=defaults.labelLarge.copy(fontSize=16.sp,fontWeight=FontWeight.SemiBold))
    MaterialTheme(colorScheme=if(night)dark else light,typography=type,
        shapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(28.dp)),content=content)
}

@Composable fun SettingsIconButton(onClick:()->Unit) {
    IconButton(onClick=onClick,modifier=Modifier.size(48.dp)) {
        Icon(Icons.Outlined.Settings,"설정",Modifier.size(28.dp))
    }
}
@Composable fun HelpIconButton() {
    LocalHelpAction.current?.let { help ->
        IconButton(onClick=help,modifier=Modifier.size(48.dp)) {Icon(Icons.Outlined.HelpOutline,"이 화면 사용 안내",Modifier.size(26.dp))}
    }
}
@Composable fun ScreenHeader(title:String,subtitle:String="",back:(()->Unit)?=null,brand:Boolean=false,actions:@Composable RowScope.()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(back!=null) IconButton(onClick=back,Modifier.size(48.dp)){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"뒤로")}
        if(brand) Bow(44)
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.headlineSmall);if(subtitle.isNotEmpty())Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        actions()
        HelpIconButton()
    }
}
@Composable fun Page(title:String,subtitle:String="",back:(()->Unit)?=null,brand:Boolean=false,
    actions:@Composable RowScope.()->Unit={},footer:(@Composable ()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A landscape keyboard can leave less room than the header + save bar.
        // Keep the focused input visible; the title/back button returns with keyboard dismissal.
        val compactInput=WindowInsets.isImeVisible && maxHeight<300.dp
        Column(Modifier.fillMaxSize()) {
        if(!compactInput)ScreenHeader(title,subtitle,back,brand,actions)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            content();Spacer(Modifier.height(10.dp))
        }
        footer?.let { Surface(color=MaterialTheme.colorScheme.background){Box(Modifier.padding(horizontal=20.dp,vertical=if(compactInput)4.dp else 10.dp)){it()}} }
        }
    }
}
@Composable fun EditorPage(title:String,subtitle:String,dirty:Boolean,back:()->Unit,save:()->Unit,enabled:Boolean=true,saveLabel:String="저장",busy:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    var discard by rememberSaveable{mutableStateOf(false)}
    // Validation errors are also unsaved input, even when the parsed model is unchanged.
    val errors=LocalInputErrors.current
    val leave={if(!busy){if(dirty || errors.isNotEmpty())discard=true else back()}}
    BackHandler {leave()}
    Page(title,subtitle,back=leave,footer={
        Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            SecondaryButton(onClick=leave,enabled=!busy,modifier=Modifier.fillMaxHeight()){Text("취소")}
            Action(saveLabel,save,enabled && !busy && errors.isEmpty(),modifier=Modifier.weight(1f).fillMaxHeight())
        }
    },content=content)
    if(discard)Confirm("변경 내용을 버릴까요?","저장하지 않은 변경만 취소해요. 기존 기록과 재고는 그대로 유지됩니다.",{discard=false}){discard=false;back()}
}

@Composable inline fun <reified T:Any> rememberJsonState(key:String,noinline initial:()->T):MutableState<T> =
    rememberSaveable(key,stateSaver=Saver<T,String>(save={codec.encodeToString(it)},restore={codec.decodeFromString<T>(it)})){mutableStateOf(initial())}

@Composable fun Paper(modifier:Modifier=Modifier,contentPadding:Dp=20.dp,content:@Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),elevation=CardDefaults.cardElevation(defaultElevation=0.dp)) {
        Column(Modifier.padding(contentPadding),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun Section(text:String) {Text(text,style=MaterialTheme.typography.titleMedium)}
@Composable fun Hint(text:String) {Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
/** Primary and outlined actions share geometry; only their emphasis colors differ. */
private val actionButtonMinHeight=54.dp
private val actionButtonPadding=PaddingValues(horizontal=18.dp,vertical=15.dp)
@Composable fun SecondaryButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    disabledContentColor:Color=Color.Unspecified,
    content:@Composable RowScope.()->Unit) {
    OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=actionButtonMinHeight),enabled=enabled,
        shape=MaterialTheme.shapes.medium,contentPadding=actionButtonPadding,
        colors=ButtonDefaults.outlinedButtonColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLowest,
            disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest,disabledContentColor=disabledContentColor),content=content)
}
/** Compact secondary action with a visible boundary and Material's 48 dp touch target. */
@Composable fun SmallButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,
    emphasized:Boolean=false,
    contentColor:Color=MaterialTheme.colorScheme.primary,
    containerColor:Color=MaterialTheme.colorScheme.surfaceContainerLowest,
    content:@Composable RowScope.()->Unit) {
    val foreground=if(emphasized)MaterialTheme.colorScheme.onPrimary else contentColor
    val background=if(emphasized)MaterialTheme.colorScheme.primary else containerColor
    val borderColor=when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha=.12f)
        emphasized -> background
        else -> foreground.copy(alpha=.45f)
    }
    OutlinedButton(onClick=onClick,modifier=modifier.defaultMinSize(minWidth=48.dp,minHeight=40.dp),enabled=enabled,
        shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
        border=BorderStroke(1.dp,borderColor),
        colors=ButtonDefaults.outlinedButtonColors(containerColor=background,contentColor=foreground,
            disabledContainerColor=MaterialTheme.colorScheme.surfaceVariant,disabledContentColor=MaterialTheme.colorScheme.onSurface.copy(alpha=.38f))) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize=14.sp,lineHeight=20.sp,textAlign=TextAlign.Center)) {content()}
    }
}
@Composable fun ButtonRow(modifier:Modifier=Modifier,centered:Boolean=false,content:@Composable FlowRowScope.()->Unit) {
    FlowRow(modifier,horizontalArrangement=Arrangement.spacedBy(8.dp,if(centered)Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement=Arrangement.spacedBy(8.dp),itemVerticalAlignment=Alignment.CenterVertically,content=content)
}
@Composable fun SelectionChip(selected:Boolean,onClick:()->Unit,label:@Composable ()->Unit,modifier:Modifier=Modifier,
    enabled:Boolean=true,leadingIcon:(@Composable ()->Unit)?=null,
    border:BorderStroke?=FilterChipDefaults.filterChipBorder(enabled,selected)) {
    FilterChip(selected=selected,onClick=onClick,label=label,modifier=modifier,enabled=enabled,leadingIcon=leadingIcon,border=border,
        shape=MaterialTheme.shapes.small,
        colors=FilterChipDefaults.filterChipColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLowest,
            disabledContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest))
}
@Composable fun MemoBlock(text:String) {
    if(text.isBlank())return
    var expanded by rememberSaveable(text){mutableStateOf(false)}
    var overflowing by remember(text){mutableStateOf(false)}
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Notes,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                Text("메모",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurface)
            }
            Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface,
                maxLines=if(expanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis,
                onTextLayout={if(!expanded)overflowing=it.hasVisualOverflow})
            if(expanded || overflowing) {
                SmallButton(onClick={expanded=!expanded}) {
                    Text(if(expanded)"접기"else"더 보기")
                }
            }
        }
    }
}
@Composable fun SelectionBox(label:String,value:String,options:List<String>,onSelect:(String)->Unit,modifier:Modifier=Modifier) {
    var expanded by remember{mutableStateOf(false)}
    Box(modifier) {
        Surface(onClick={expanded=true},shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest,
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
            modifier=Modifier.fillMaxWidth().semantics{contentDescription=label;stateDescription=value}) {
            Column(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(value,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Outlined.ExpandMore,null,Modifier.size(18.dp))
                }
            }
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            options.forEach{option->DropdownMenuItem(text={Text(option)},onClick={expanded=false;onSelect(option)})}
        }
    }
}
@Composable fun Action(text:String,onClick:()->Unit,enabled:Boolean=true,icon:ImageVector?=null,modifier:Modifier=Modifier) {
    val colors=MaterialTheme.colorScheme
    Button(onClick,modifier.fillMaxWidth().heightIn(min=actionButtonMinHeight),enabled=enabled,shape=MaterialTheme.shapes.medium,
        contentPadding=actionButtonPadding,colors=ButtonDefaults.buttonColors(containerColor=colors.primary,contentColor=colors.onPrimary,disabledContainerColor=colors.surfaceVariant)) {
        if(icon!=null){Icon(icon,null,Modifier.size(22.dp));Spacer(Modifier.width(8.dp))};Text(text,textAlign=TextAlign.Center)
    }
}
@Composable fun Bow(size:Int=44) {Image(painterResource(R.drawable.journal_bow),stringResource(R.string.app_name),Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)))}
@Composable fun Bookmark(modifier:Modifier=Modifier) {
    Canvas(modifier.size(30.dp,44.dp)) {
        val shape=Path().apply{moveTo(0f,0f);lineTo(size.width,0f);lineTo(size.width,size.height);lineTo(size.width/2,size.height*.78f);lineTo(0f,size.height);close()}
        drawPath(shape,Brush.horizontalGradient(listOf(Coral,Color(0xFFFFA08A))))
    }
}
@Composable fun ColorDot(color:Long,size:Int=16,label:String?=null) {
    val hex=String.format(Locale.US,"#%06X",color and 0xFFFFFF)
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(color),Color(color).copy(alpha=.8f))))
        .border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.6f),CircleShape)
        .semantics{contentDescription=label?.let{"$it 색상 $hex"} ?: "품목 색상 $hex"})
}
@Composable fun ProductLine(s:Snapshot,item:Item,compact:Boolean=false) {
    val product=s.products.find{it.id==item.productId}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        ColorDot(product?.color ?: 0xFF647789,if(compact)14 else 20,product?.name ?: item.name)
        Text(product?.name ?: item.name,Modifier.weight(1f),style=if(compact)MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
        Text("${item.quantity} EA",fontWeight=FontWeight.SemiBold)
    }
}
@Composable fun ProductChips(s:Snapshot,items:List<Item>) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items.forEach{item->val p=s.products.find{it.id==item.productId}
            Surface(shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    ColorDot(p?.color ?: 0xFF647789,14,p?.name ?: item.name)
                    Text("${p?.name ?: item.name} × ${item.quantity}EA",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}
@Composable fun MenuRow(title:String,subtitle:String="",icon:ImageVector=Icons.Outlined.ChevronRight,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=56.dp).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);if(subtitle.isNotBlank())Hint(subtitle)}
        Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun AdaptivePair(first:@Composable ()->Unit,second:@Composable ()->Unit) {
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<300.dp || fontScale>1.25f)Column(verticalArrangement=Arrangement.spacedBy(14.dp)){first();second()}
        else Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){Column(Modifier.weight(1f)){first()};Column(Modifier.weight(1f)){second()}}
    }
}
@Composable fun calendarDayColor(day:DayOfWeek,normal:Color=MaterialTheme.colorScheme.onSurface):Color = when(day) {
    DayOfWeek.SUNDAY->MaterialTheme.colorScheme.error
    DayOfWeek.SATURDAY->MaterialTheme.colorScheme.primary
    else->normal
}
@Composable fun CalendarWeekdayHeader() {
    Row(Modifier.semantics { contentDescription="달력 요일" }) {
        listOf("일","월","화","수","목","금","토").forEachIndexed{index,label->
            Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                Text(label,style=MaterialTheme.typography.bodyMedium,
                    color=calendarDayColor(DayOfWeek.of(if(index==0)7 else index),MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}
@Composable fun DateControl(date:String,onChange:(String)->Unit,label:String="날짜",showQuickDates:Boolean=false) {
    var open by rememberSaveable{mutableStateOf(false)}
    var month by rememberSaveable{mutableStateOf(date.ifBlank{today()}.take(7))}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(date.isBlank())"$label 선택해 주세요"else"$label ${date.replace('-', '.')}",fontWeight=FontWeight.SemiBold)
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
            FlowRow(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                if(showQuickDates) {
                    SelectionChip(date==today(),{onChange(today())},{Text("오늘")},border=null)
                    SelectionChip(date==LocalDate.now().minusDays(1).toString(),{onChange(LocalDate.now().minusDays(1).toString())},{Text("어제")},border=null)
                }
                SmallButton(onClick={month=date.ifBlank{today()}.take(7);open=!open}){Icon(Icons.Outlined.CalendarMonth,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(open)"날짜 선택 접기"else"날짜 선택")}
            }
        }
        if(open)Paper(contentPadding=8.dp) {
            val ym=YearMonth.parse(month)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                IconButton(onClick={month=ym.minusMonths(1).toString()}){Icon(Icons.Outlined.ChevronLeft,"이전 달")}
                FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(4.dp,Alignment.CenterHorizontally)) {
                    listOf("${ym.year}년","${ym.monthValue}월").forEach {
                        Text(it,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,softWrap=false)
                    }
                }
                IconButton(onClick={month=ym.plusMonths(1).toString()}){Icon(Icons.Outlined.ChevronRight,"다음 달")}
            }
            Column(Modifier.fillMaxWidth()) {
                CalendarWeekdayHeader()
                val offset=ym.atDay(1).dayOfWeek.value%7
                repeat((offset+ym.lengthOfMonth()+6)/7){w->Row {
                    repeat(7){d->val day=w*7+d-offset+1
                        if(day !in 1..ym.lengthOfMonth())Spacer(Modifier.weight(1f).height(48.dp))else {
                            val selected=ym.atDay(day).toString()
                            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                                .background(if(selected==date)MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable(role=Role.Button){onChange(selected);open=false}.semantics{contentDescription=selected;this.selected=selected==date},contentAlignment=Alignment.Center){Text(day.toString(),color=calendarDayColor(ym.atDay(day).dayOfWeek))}
                        }
                    }
                }}
            }
        }
    }
}
@Composable fun NumberInput(label:String,value:Int?,onChange:(Int?)->Unit,unit:String,scale:Int=1,signed:Boolean=false,large:Boolean=false) {
    fun formatted(number:Int?)=number?.let{BigDecimal(it).divide(BigDecimal(scale)).stripTrailingZeros().toPlainString()} ?: ""
    var text by rememberSaveable{mutableStateOf(formatted(value))}
    var emitted by rememberSaveable{mutableStateOf(value)}
    var invalid by rememberSaveable{mutableStateOf(false)}
    val errors=LocalInputErrors.current
    LaunchedEffect(value) {
        if(value!=emitted){text=formatted(value);emitted=value;invalid=false}
    }
    val errorId=rememberSaveable{newId()}
    DisposableEffect(errorId){onDispose{errors.remove(errorId)}}
    LaunchedEffect(invalid){if(invalid)errors[errorId]=label else errors.remove(errorId)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value=text,onValueChange={entered->
            if(entered.matches(Regex(if(signed)"-?[0-9]*([.,][0-9]*)?"else"[0-9]*([.,][0-9]*)?"))) {
                text=entered
                val converted=try{entered.replace(',','.').toBigDecimalOrNull()?.multiply(BigDecimal(scale))?.intValueExact()}catch(_:ArithmeticException){null}
                invalid=entered.isNotBlank() && converted==null
                if(invalid)errors[errorId]=label else errors.remove(errorId)
                // Keep malformed text and its error visible. Emitting null here lets
                // callers with a zero default immediately reset the field to 0.
                if(!invalid) {emitted=converted;onChange(converted)}
            }
        },placeholder={Text("—")},suffix={Text(unit,style=MaterialTheme.typography.bodyMedium)},isError=invalid,
            supportingText=if(invalid){{Text("숫자와 소수 자릿수를 확인해 주세요")}}else null,
            keyboardOptions=KeyboardOptions(keyboardType=if(scale>1)KeyboardType.Decimal else KeyboardType.Number),
            trailingIcon=if(!large){{IconButton(onClick={text="";emitted=null;invalid=false;errors.remove(errorId);onChange(null)}){Icon(Icons.Outlined.Close,"$label 전체 지우기",Modifier.size(20.dp))}}}else null,
            textStyle=MaterialTheme.typography.titleLarge.copy(fontSize=if(large)28.sp else 22.sp),
            shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().semantics{contentDescription=label},singleLine=true,
            colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outline,focusedBorderColor=MaterialTheme.colorScheme.primary,
                unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest,focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest))
        if(large && value!=null)SmallButton(onClick={text="";emitted=null;invalid=false;errors.remove(errorId);onChange(null)}){Text("지우기")}
    }
}
@Composable fun Confirm(title:String,text:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(text)},containerColor=MaterialTheme.colorScheme.surface,
        confirmButton={SmallButton(onClick=onConfirm,emphasized=true){Text("확인")}},dismissButton={SmallButton(onClick=onDismiss){Text("취소")}})
}
