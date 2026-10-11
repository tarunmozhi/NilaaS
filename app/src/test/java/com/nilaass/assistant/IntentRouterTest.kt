package com.nilaass.assistant

import com.nilaass.assistant.core.IntentRouter
import com.nilaass.assistant.core.LocalIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentRouterTest {
    private val router = IntentRouter()
    @Test fun recognizesHelp() = assertEquals(LocalIntent.Help, router.route("help"))
    @Test fun recognizesTamilHelp() = assertEquals(LocalIntent.Help, router.route("உதவி"))
    @Test fun unknownCommandIsSafe() = assertEquals(LocalIntent.Unknown("delete everything"), router.route("delete everything"))
}
