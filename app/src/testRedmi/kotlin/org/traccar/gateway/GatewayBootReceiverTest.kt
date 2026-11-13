package org.traccar.gateway

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE, application = Application::class)
class GatewayBootReceiverTest {
    @Test fun `boot and update respect the persisted toggle`() {
        val app = RuntimeEnvironment.getApplication()
        val receiver = GatewayBootReceiver()
        val shadow = shadowOf(app)

        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadow.nextStartedService)

        check(GatewaySettings.setEnabled(app, true))
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(ComponentName(app, GatewayService::class.java), shadow.nextStartedService?.component)

        check(GatewaySettings.setEnabled(app, false))
        receiver.onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertNull(shadow.nextStartedService)
    }
}
