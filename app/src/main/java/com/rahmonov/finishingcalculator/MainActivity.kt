package com.rahmonov.finishingcalculator

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {
    private enum class Step { INPUT, ESTIMATE, FINAL }

    private data class OpeningInput(
        var width: String = "",
        var height: String = ""
    )

    private data class RoomInput(
        var floorArea: String = "",
        var length: String = "",
        var width: String = "",
        var ceilingHeight: String = "",
        var innerCorners: String = "",
        var outerCorners: String = "",
        val openings: MutableList<OpeningInput> = mutableListOf()
    )

    private data class MaterialItem(
        val title: String,
        val quantity: Double,
        val unit: String,
        val manual: Boolean = false
    )

    private val rooms = mutableListOf(RoomInput())
    private val autoMaterials = mutableListOf<MaterialItem>()
    private val manualMaterials = mutableListOf<MaterialItem>()
    private var currentStep = Step.INPUT
    private var includeCeiling = true
    private val numberFormat = DecimalFormat("#.#", DecimalFormatSymbols(Locale.US))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showInputScreen()
    }

    private fun showInputScreen() {
        currentStep = Step.INPUT
        val content = verticalRoot()
        content.addView(title("Параметры комнат"))
        content.addView(subtitle("Заполните размеры, добавьте проемы и выберите режим шпаклевки."))

        val roomCountInput = editText("Количество комнат", rooms.size.toString(), decimal = false)
        roomCountInput.inputType = InputType.TYPE_CLASS_NUMBER
        roomCountInput.addTextChangedListener(simpleWatcher {
            val count = roomCountInput.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: 1
            if (count != rooms.size) {
                resizeRooms(count)
                showInputScreen()
            }
        })
        content.addView(roomCountInput)

        rooms.forEachIndexed { index, room ->
            content.addView(roomCard(index, room))
        }

        content.addView(modeSelector())
        content.addView(primaryButton("Рассчитать") {
            autoMaterials.clear()
            autoMaterials.addAll(calculateMaterials())
            showEstimateScreen()
        })

        setContentView(scroll(content))
    }

    private fun showEstimateScreen() {
        currentStep = Step.ESTIMATE
        val content = verticalRoot()
        content.addView(title("Предварительная смета"))
        content.addView(subtitle("Автоматические позиции защищены от удаления. Свои материалы можно добавить ниже."))
        content.addView(materialList(autoMaterials + manualMaterials, showDelete = true))
        content.addView(addManualMaterialBlock())
        content.addView(primaryButton("Далее (Сформировать)") { showFinalScreen() })
        content.addView(secondaryButton("Назад к параметрам") { showInputScreen() })
        setContentView(scroll(content))
    }

    private fun showFinalScreen() {
        currentStep = Step.FINAL
        val content = verticalRoot()
        content.addView(title("Финальная смета"))
        content.addView(subtitle("Готовый список материалов для отправки или копирования."))
        content.addView(materialList(autoMaterials + manualMaterials, showDelete = false))
        content.addView(primaryButton("Поделиться сметой") { shareEstimate() })
        content.addView(secondaryButton("Назад к смете") { showEstimateScreen() })
        setContentView(scroll(content))
    }

    private fun roomCard(index: Int, room: RoomInput): View {
        val card = card()
        card.addView(sectionTitle("Комната №${index + 1}"))
        card.addView(boundEditText("Площадь по полу (кв.м)", room.floorArea) { room.floorArea = it })
        card.addView(boundEditText("Длина (м)", room.length) { room.length = it })
        card.addView(boundEditText("Ширина (м)", room.width) { room.width = it })
        card.addView(boundEditText("Высота потолков (м)", room.ceilingHeight) { room.ceilingHeight = it })
        card.addView(boundEditText("Внутренние углы (шт.)", room.innerCorners, decimal = false) { room.innerCorners = it })
        card.addView(boundEditText("Внешние углы (шт.)", room.outerCorners, decimal = false) { room.outerCorners = it })

        card.addView(sectionTitle("Окна и двери"))
        if (room.openings.isEmpty()) {
            card.addView(muted("Проемы пока не добавлены."))
        }
        room.openings.forEachIndexed { openingIndex, opening ->
            card.addView(openingRow(room, openingIndex, opening))
        }
        card.addView(secondaryButton("+ Добавить проем") {
            room.openings.add(OpeningInput())
            showInputScreen()
        })
        return card
    }

    private fun openingRow(room: RoomInput, openingIndex: Int, opening: OpeningInput): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Проем №${openingIndex + 1}"
            setTextColor(Color.rgb(45, 52, 65))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(iconButton("×") {
            room.openings.removeAt(openingIndex)
            showInputScreen()
        })
        row.addView(header)
        row.addView(boundEditText("Ширина проема (м)", opening.width) { opening.width = it })
        row.addView(boundEditText("Высота проема (м)", opening.height) { opening.height = it })
        return row
    }

    private fun modeSelector(): View {
        val card = card()
        card.addView(sectionTitle("Режим шпаклевки"))
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val withCeiling = RadioButton(this).apply {
            text = "С потолком"
            id = View.generateViewId()
            isChecked = includeCeiling
            textSize = 15f
        }
        val withoutCeiling = RadioButton(this).apply {
            text = "Без потолка"
            id = View.generateViewId()
            isChecked = !includeCeiling
            textSize = 15f
        }
        group.addView(withCeiling)
        group.addView(withoutCeiling)
        group.setOnCheckedChangeListener { _, checkedId ->
            includeCeiling = checkedId == withCeiling.id
        }
        card.addView(group)
        return card
    }

    private fun addManualMaterialBlock(): View {
        val card = card()
        card.addView(sectionTitle("Добавить свой материал"))
        val nameInput = editText("Название материала", "", decimal = false).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val countInput = editText("Количество", "")
        card.addView(nameInput)
        card.addView(countInput)
        card.addView(secondaryButton("+ Добавить") {
            val name = nameInput.text.toString().trim()
            val count = countInput.text.toString().toDoubleValue()
            if (name.isBlank() || count <= 0.0) {
                Toast.makeText(this, "Введите название и количество больше 0", Toast.LENGTH_SHORT).show()
                return@secondaryButton
            }
            manualMaterials.add(MaterialItem(name, count, "шт", manual = true))
            showEstimateScreen()
        })
        return card
    }

    private fun materialList(items: List<MaterialItem>, showDelete: Boolean): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        items.forEachIndexed { _, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedBackground(Color.WHITE, Color.rgb(224, 229, 238), dp(8))
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, dp(10))
                layoutParams = params
            }
            row.addView(TextView(this).apply {
                text = "${item.title}: ${format(item.quantity)} ${item.unit}${if (item.manual) " (добавлено вручную)" else ""}"
                textSize = 16f
                setTextColor(Color.rgb(31, 36, 47))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (showDelete && item.manual) {
                row.addView(iconButton("×") {
                    manualMaterials.remove(item)
                    showEstimateScreen()
                })
            }
            list.addView(row)
        }
        return list
    }

    private fun calculateMaterials(): List<MaterialItem> {
        val totalFloorArea = rooms.sumOf { it.floorArea.toDoubleValue() }
        val openingsArea = rooms.sumOf { room ->
            room.openings.sumOf { it.width.toDoubleValue() * it.height.toDoubleValue() }
        }
        val floorCardboard = totalFloorArea * 1.2
        val windowFilm = openingsArea * 1.2
        val primer = (totalFloorArea / 6.0).roundOne()
        val putty = totalFloorArea * if (includeCeiling) 10.0 else 8.0

        return listOf(
            MaterialItem("Картон для пола", floorCardboard, "кв.м"),
            MaterialItem("Целлофан для окон", windowFilm, "кв.м"),
            MaterialItem("Грунтовка", primer, "л"),
            MaterialItem("Шпаклевка", putty, "кг")
        )
    }

    private fun shareEstimate() {
        val text = buildEstimateText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Поделиться сметой"))
    }

    private fun buildEstimateText(): String {
        val lines = mutableListOf("Смета материалов:")
        (autoMaterials + manualMaterials).forEach { item ->
            lines.add("${item.title}: ${format(item.quantity)} ${item.unit}${if (item.manual) " (добавлено вручную)" else ""}")
        }
        return lines.joinToString("\n")
    }

    private fun resizeRooms(count: Int) {
        while (rooms.size < count) rooms.add(RoomInput())
        while (rooms.size > count) rooms.removeAt(rooms.lastIndex)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Android Activity back navigation API is deprecated, but this app has no AndroidX dependency.")
    override fun onBackPressed() {
        when (currentStep) {
            Step.INPUT -> super.onBackPressed()
            Step.ESTIMATE -> showInputScreen()
            Step.FINAL -> showEstimateScreen()
        }
    }

    private fun verticalRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(22), dp(18), dp(24))
        setBackgroundColor(Color.rgb(247, 248, 250))
    }

    private fun scroll(content: View): ScrollView = ScrollView(this).apply {
        setBackgroundColor(Color.rgb(247, 248, 250))
        addView(content)
    }

    private fun title(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 25f
        setTextColor(Color.rgb(23, 29, 43))
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(6))
    }

    private fun subtitle(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 15f
        setTextColor(Color.rgb(95, 105, 121))
        setPadding(0, 0, 0, dp(14))
    }

    private fun sectionTitle(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 18f
        setTextColor(Color.rgb(31, 36, 47))
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    }

    private fun muted(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 14f
        setTextColor(Color.rgb(111, 121, 138))
        setPadding(0, dp(2), 0, dp(10))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = roundedBackground(Color.WHITE, Color.rgb(226, 231, 239), dp(8))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(14))
        layoutParams = params
    }

    private fun boundEditText(
        hint: String,
        value: String,
        decimal: Boolean = true,
        onChange: (String) -> Unit
    ): EditText {
        return editText(hint, value, decimal).apply {
            addTextChangedListener(simpleWatcher { onChange(text.toString()) })
        }
    }

    private fun editText(hintText: String, value: String, decimal: Boolean = true): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(value)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_NEXT
            inputType = if (decimal) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_NUMBER
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(Color.WHITE, Color.rgb(203, 211, 224), dp(8))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            params.setMargins(0, 0, 0, dp(10))
            layoutParams = params
        }
    }

    private fun primaryButton(textValue: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = textValue
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.rgb(47, 111, 237), Color.TRANSPARENT, dp(8))
            setOnClickListener { onClick() }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
            params.setMargins(0, dp(6), 0, dp(10))
            layoutParams = params
        }
    }

    private fun secondaryButton(textValue: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = textValue
            textSize = 15f
            setTextColor(Color.rgb(47, 111, 237))
            background = roundedBackground(Color.rgb(239, 244, 255), Color.rgb(195, 211, 244), dp(8))
            setOnClickListener { onClick() }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            params.setMargins(0, dp(4), 0, dp(4))
            layoutParams = params
        }
    }

    private fun iconButton(textValue: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = textValue
            textSize = 20f
            setTextColor(Color.rgb(179, 44, 44))
            background = roundedBackground(Color.rgb(255, 239, 239), Color.rgb(247, 194, 194), dp(8))
            minWidth = dp(42)
            minHeight = dp(42)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
    }

    private fun roundedBackground(fill: Int, stroke: Int, radius: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun simpleWatcher(afterChanged: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = afterChanged()
        }
    }

    private fun String.toDoubleValue(): Double {
        return replace(',', '.').toDoubleOrNull()?.let { max(0.0, it) } ?: 0.0
    }

    private fun Double.roundOne(): Double = kotlin.math.round(this * 10.0) / 10.0

    private fun format(value: Double): String = numberFormat.format(value.roundOne())

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
