package zadudoder.qrscanner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.net.URI;
import java.nio.ByteBuffer;

public class QRScannerClient implements ClientModInitializer {
    private static KeyBinding scanKey;

    @Override
    public void onInitializeClient() {
        scanKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.qrscanner.scan",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.qrscanner.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (scanKey.wasPressed()) {
                scanForQRCode(client);
            }
        });
    }

    public void scanForQRCode(MinecraftClient client) {
        if (client.player == null) return;

        try {
            BufferedImage screenshot = captureScreen();
            if (screenshot == null) {
                client.player.sendMessage(Text.translatable("text.FailedTakeScreenshot"), false);
                return;
            }
            String result = decodeQRCode(screenshot);

            if (result != null) {
                Text clickableLink = Text.literal(result)
                        .styled(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, result))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Text.translatable("text.qrscanner.hover_tip")))
                        );

                client.player.sendMessage(
                        Text.translatable("text.foundLink", clickableLink),
                        false
                );
                client.setScreen(new AcceptScreen(result, client.currentScreen));
            } else {
                client.player.sendMessage(Text.translatable("text.QRCodeNotFound"), false);
            }
        } catch (Exception e) {
            client.player.sendMessage(Text.translatable("text.error", e.getClass().getSimpleName()), false);
            e.printStackTrace();
        }
    }

    public BufferedImage captureScreen() {
        try {
            GLFW.glfwPollEvents();
            int width = MinecraftClient.getInstance().getWindow().getWidth();
            int height = MinecraftClient.getInstance().getWindow().getHeight();

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

            ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
            GLFW.glfwGetFramebufferSize(MinecraftClient.getInstance().getWindow().getHandle(),
                    new int[1], new int[1]);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

            buffer.asIntBuffer().get(pixels);
            return image;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String decodeQRCode(BufferedImage image) {
        try {
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());

            com.google.zxing.LuminanceSource source = new com.google.zxing.RGBLuminanceSource(
                    image.getWidth(),
                    image.getHeight(),
                    pixels
            );

            com.google.zxing.BinaryBitmap bitmap = new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(source)
            );

            com.google.zxing.Result result = new com.google.zxing.qrcode.QRCodeReader().decode(bitmap);
            return result.getText();
        } catch (Exception e) {
            return null;
        }
    }
}
