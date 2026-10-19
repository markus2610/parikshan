package io.github.aryapreetam.parikshan.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolSerializationTest {

    @Test
    fun testResponseSerialization() {
        val json = """{"type": "error", "id": "123", "message": "test error"}"""
        val resp = ProtocolJson.decodeResponse(json)
        assertTrue(resp is Response.Error)
        assertEquals("123", resp.id)
        assertEquals("test error", resp.message)

        val okJson = """{"type": "ok", "id": "456"}"""
        val okResp = ProtocolJson.decodeResponse(okJson)
        assertTrue(okResp is Response.Ok)
        assertEquals("456", okResp.id)

        val nodeInfoJson = """{"type": "node_info", "id": "789", "bounds": {"left": 0.0, "top": 0.0, "right": 100.0, "bottom": 100.0}, "visible": true}"""
        val nodeInfoResp = ProtocolJson.decodeResponse(nodeInfoJson)
        assertTrue(nodeInfoResp is Response.NodeInfo)
        assertEquals("789", nodeInfoResp.id)
    }

    @Test
    fun testCommandSerialization() {
        val pingJson = """{"type": "ping", "id": "p1"}"""
        val pingCmd = ProtocolJson.decodeCommand(pingJson)
        assertTrue(pingCmd is Command.Ping)
        assertEquals("p1", pingCmd.id)

        val getTreeJson = """{"type": "gettree", "id": "t1"}"""
        val getTreeCmd = ProtocolJson.decodeCommand(getTreeJson)
        assertTrue(getTreeCmd is Command.GetTree)
        assertEquals("t1", getTreeCmd.id)

        val relaunchJson = """{"type": "relaunchapp", "id": "r1"}"""
        val relaunchCmd = ProtocolJson.decodeCommand(relaunchJson)
        assertTrue(relaunchCmd is Command.RelaunchApp)
        assertEquals("r1", relaunchCmd.id)
    }

    @Test
    fun testFilePickerCommandsRoundTrip() {
        val queued = Command.QueueFilePickerResult(id = "f1", mode = FilePickerMode.Files, paths = listOf("/tmp/a.png", "/tmp/b.png"))
        val json = ProtocolJson.encodeCommand(queued)
        assertTrue(json.contains("\"type\":\"queuefilepicker\""), json)
        assertTrue(json.contains("\"mode\":\"files\""), json)
        assertEquals(queued, ProtocolJson.decodeCommand(json))

        val cancel = ProtocolJson.decodeCommand("""{"type": "queuefilepicker", "id": "f2", "mode": "cancel"}""")
        assertEquals(Command.QueueFilePickerResult(id = "f2", mode = FilePickerMode.Cancel), cancel)

        val verify = ProtocolJson.decodeCommand("""{"type": "verifyfilepicker", "id": "v1"}""")
        assertEquals(Command.VerifyFilePickerQueue(id = "v1"), verify)
    }

    @Test
    fun testCommandTokenRoundTrip() {
        val command = Command.Ping(id = "p1", token = "token-123")
        val json = ProtocolJson.encodeCommand(command)
        val decoded = ProtocolJson.decodeCommand(json)

        assertTrue(decoded is Command.Ping)
        assertEquals("token-123", decoded.token)
    }
}
