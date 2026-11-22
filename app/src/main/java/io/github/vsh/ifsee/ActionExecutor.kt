package io.github.vsh.ifsee

import io.github.vsh.ifsee.model.RuleAction

interface ActionExecutor {
    fun execute(action: RuleAction)
}

class ScreenshotActionExecutor(private val onScreenshot: () -> Unit) : ActionExecutor {
    override fun execute(action: RuleAction) {
        if (action is RuleAction.Screenshot) {
            onScreenshot()
        }
    }
}
