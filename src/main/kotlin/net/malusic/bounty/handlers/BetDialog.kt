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
    private val messageManager: MessageHandler
) {
    fun openBet(player: Player, targetName: String, currentAmount: String): Boolean =
        open(player) {
            val title = text("dialog.bet.title", mapOf("player" to targetName))
            val body = text(
                "dialog.bet.body",
                mapOf("player" to targetName, "amount" to currentAmount)
            )

            val base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .body(listOf(DialogBody.plainMessage(body)))
                .inputs(
                    listOf(
                        DialogInput.text("amount", text("dialog.bet.amount"))
                            .width(200)
                            .labelVisible(true)
                            .maxLength(32)
                            .build()
                    )
                )
                .build()

            val confirm = button(
                text("dialog.bet.confirm"),
                text("dialog.bet.confirm-tooltip"),
                DialogAction.commandTemplate("bounty bet $targetName \$(amount)")
            )
            val cancel = button(
                text("dialog.bet.cancel"),
                text("dialog.bet.cancel-tooltip"),
                DialogAction.staticAction(ClickEvent.runCommand("/bounty"))
            )

            Dialog.create { factory ->
                factory.empty()
                    .base(base)
                    .type(DialogType.confirmation(confirm, cancel))
            }
        }

    fun openSet(player: Player): Boolean =
        open(player) {
            val base = DialogBase.builder(text("dialog.set.title"))
                .canCloseWithEscape(true)
                .pause(false)
                .body(listOf(DialogBody.plainMessage(text("dialog.set.body"))))
                .inputs(
                    listOf(
                        DialogInput.text("player", text("dialog.set.player"))
                            .width(200)
                            .labelVisible(true)
                            .maxLength(16)
                            .build(),
                        DialogInput.text("amount", text("dialog.set.amount"))
                            .width(200)
                            .labelVisible(true)
                            .maxLength(32)
                            .build()
                    )
                )
                .build()

            val confirm = button(
                text("dialog.set.confirm"),
                text("dialog.set.confirm-tooltip"),
                DialogAction.commandTemplate("bounty set \$(player) \$(amount)")
            )
            val cancel = button(
                text("dialog.set.cancel"),
                text("dialog.set.cancel-tooltip"),
                DialogAction.staticAction(ClickEvent.runCommand("/bounty"))
            )

            Dialog.create { factory ->
                factory.empty()
                    .base(base)
                    .type(DialogType.confirmation(confirm, cancel))
            }
        }

    fun openNew(player: Player): Boolean =
        open(player) {
            val base = DialogBase.builder(text("dialog.new.title"))
                .canCloseWithEscape(true)
                .pause(false)
                .body(listOf(DialogBody.plainMessage(text("dialog.new.body"))))
                .inputs(
                    listOf(
                        DialogInput.text("player", text("dialog.new.player"))
                            .width(200)
                            .labelVisible(true)
                            .maxLength(16)
                            .build(),
                        DialogInput.text("amount", text("dialog.new.amount"))
                            .width(200)
                            .labelVisible(true)
                            .maxLength(32)
                            .build()
                    )
                )
                .build()

            val confirm = button(
                text("dialog.new.confirm"),
                text("dialog.new.confirm-tooltip"),
                DialogAction.commandTemplate("bounty bet \$(player) \$(amount)")
            )
            val cancel = button(
                text("dialog.new.cancel"),
                text("dialog.new.cancel-tooltip"),
                DialogAction.staticAction(ClickEvent.runCommand("/bounty"))
            )

            Dialog.create { factory ->
                factory.empty()
                    .base(base)
                    .type(DialogType.confirmation(confirm, cancel))
            }
        }

    private fun open(player: Player, dialog: () -> Dialog): Boolean =
        try {
            player.showDialog(dialog())
            true
        } catch (exception: Exception) {
            plugin.logger.log(Level.FINE, "Dialog API unavailable; using chat fallback.", exception)
            false
        } catch (error: LinkageError) {
            plugin.logger.log(Level.FINE, "Dialog API unavailable; using chat fallback.", error)
            false
        }

    private fun button(
        label: Component,
        tooltip: Component,
        action: DialogAction
    ): ActionButton =
        ActionButton.builder(label)
            .tooltip(tooltip)
            .width(150)
            .action(action)
            .build()

    private fun text(
        path: String,
        placeholders: Map<String, String> = emptyMap()
    ): Component = messageManager.message(path, placeholders, withPrefix = false)
}
