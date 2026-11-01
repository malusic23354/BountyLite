package net.malusic.bounty

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class BetDialog(
    private val plugin: JavaPlugin,
    private val messages: MessageHandler
) {

    fun openBet(player: Player, targetName: String, currentAmount: String): Boolean =
        show(
            player,
            "bet",
            mapOf("player" to targetName, "amount" to currentAmount),
            listOf("amount" to 32),
            "bounty bet $targetName \$(amount)"
        )

    fun openNew(player: Player): Boolean =
        show(
            player,
            "new",
            emptyMap(),
            listOf("player" to 16, "amount" to 32),
            "bounty bet \$(player) \$(amount)"
        )

    private fun show(
        player: Player,
        key: String,
        placeholders: Map<String, String>,
        inputs: List<Pair<String, Int>>,
        command: String
    ): Boolean =
        try {
            player.showDialog(build(key, placeholders, inputs, command))
            true
        } catch (exception: Exception) {
            unavailable(exception)
        } catch (error: LinkageError) {
            unavailable(error)
        }

    private fun build(
        key: String,
        placeholders: Map<String, String>,
        inputs: List<Pair<String, Int>>,
        command: String
    ): Dialog {
        fun text(path: String) = messages.message("dialog.$key.$path", placeholders, withPrefix = false)

        val base = DialogBase.builder(text("title"))
            .canCloseWithEscape(true)
            .pause(false)
            .body(listOf(DialogBody.plainMessage(text("body"))))
            .inputs(
                inputs.map { (name, length) ->
                    DialogInput.text(name, text(name)).width(200).labelVisible(true).maxLength(length).build()
                }
            )
            .build()

        val confirm = button(text("confirm"), text("confirm-tooltip"), DialogAction.commandTemplate(command))
        val cancel = button(text("cancel"), text("cancel-tooltip"), DialogAction.staticAction(ClickEvent.runCommand("/bounty")))

        return Dialog.create { factory ->
            factory.empty()
                .base(base)
                .type(DialogType.confirmation(confirm, cancel))
        }
    }

    private fun button(label: Component, tooltip: Component, action: DialogAction): ActionButton =
        ActionButton.builder(label).tooltip(tooltip).width(150).action(action).build()

    private fun unavailable(cause: Throwable): Boolean {
        plugin.logger.log(Level.WARNING, "Dialog failed; using chat fallback.", cause)
        return false
    }
}