package zadudoder.qrscanner;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

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
                ScanQrCode(client);
            }
        });
    }

    public static void ScanQrCode(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        NativeImage screenshot;
        try {
            screenshot = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            if (screenshot == null) {
                client.player.sendMessage(Text.translatable("text.FailedTakeScreenshot"), false);
                return;
            }
        } catch (Exception ex) {
            return;
        }
        BufferedImage bufferedImage = new BufferedImage(
                screenshot.getWidth(),
                screenshot.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        for (int y = 0; y < screenshot.getHeight(); y++) {
            for (int x = 0; x < screenshot.getWidth(); x++) {
                int color = screenshot.getColor(x, y);
                bufferedImage.setRGB(x, y, color);
            }
        }
        String result = decodeQRCode(bufferedImage);
        if (result == null) {
            client.player.sendMessage(Text.translatable("text.QRCodeNotFound"), false);
            return;
        }

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
    }

    private static String decodeQRCode(BufferedImage image) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);

        LuminanceSource source = new BufferedImageLuminanceSource(image);

        String result = tryDecodeWithStrategies(source, hints);

        if (result == null) {
            hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
            result = tryDecodeWithStrategies(source, hints);
        }

        return result;
    }

    private static String tryDecodeWithStrategies(LuminanceSource source, Map<DecodeHintType, Object> hints) {
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            return new MultiFormatReader().decode(bitmap, hints).getText();
        } catch (NotFoundException e) {
        }

        try {
            BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            return new MultiFormatReader().decode(bitmap, hints).getText();
        } catch (NotFoundException e) {
        }
        try {
            GenericMultipleBarcodeReader reader = new GenericMultipleBarcodeReader(new MultiFormatReader());
            Result[] results = reader.decodeMultiple(new BinaryBitmap(new HybridBinarizer(source)), hints);
            if (results.length > 0) {
                return results[0].getText();
            }
        } catch (NotFoundException e) {
        }

        return null;
    }
}
