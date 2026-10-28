package com.dotpet.editor;

import com.dotpet.animation.Frame;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Reads one image into the current canvas without changing other animation frames. */
final class FrameImageImporter {
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_PIXELS = 16L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;

    private FrameImageImporter() { }

    static Frame read(Path path, int canvasSize) throws IOException {
        if (canvasSize != 16 && canvasSize != 32 && canvasSize != 64)
            throw new IllegalArgumentException("Unsupported pixel canvas size.");
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("이미지 파일이 너무 큽니다. 32MB 이하 파일을 선택하세요.");
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("이미지 파일을 열 수 없습니다.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("지원하지 않거나 손상된 이미지입니다. PNG, JPG, BMP 파일을 선택하세요.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS)
                    throw new IOException("이미지 해상도가 너무 큽니다. 더 작은 이미지를 선택하세요.");
                return fitToCanvas(reader.read(0), canvasSize);
            } finally {
                reader.dispose();
            }
        }
    }

    private static Frame fitToCanvas(BufferedImage image, int size) throws IOException {
        if (image == null) throw new IOException("이미지를 읽을 수 없습니다.");
        double scale = Math.min((double) size / image.getWidth(), (double) size / image.getHeight());
        int width = Math.max(1, Math.min(size, (int) Math.round(image.getWidth() * scale)));
        int height = Math.max(1, Math.min(size, (int) Math.round(image.getHeight() * scale)));
        int left = (size - width) / 2, top = (size - height) / 2;
        int[] pixels = new int[size * size];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[(top + y) * size + left + x] = image.getRGB(
                        x * image.getWidth() / width, y * image.getHeight() / height);
            }
        }
        return new Frame(size, size, pixels);
    }
}
