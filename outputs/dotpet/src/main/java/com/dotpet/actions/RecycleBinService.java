package com.dotpet.actions;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32;
import com.dotpet.settings.SettingsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Explicit file recycling and confirmed emptying; contains no permanent-file-delete fallback. */
public class RecycleBinService {
    public record BinInfo(long items, long bytes) { }
    public record Failure(Path path, String reason) { }
    public record Result(int moved, List<Failure> failures) {
        public Result { failures = List.copyOf(failures); }
        public String summary() {
            String message = moved + "개 파일을 휴지통으로 보냈어요.";
            if (!failures.isEmpty()) message += " " + failures.size() + "개 실패: " + failures.getFirst().path().getFileName() + " · " + failures.getFirst().reason();
            return message;
        }
    }
    private final WindowsRecycleBin shell = new WindowsRecycleBin();
    private final SettingsService settings;

    public RecycleBinService() { this(new SettingsService()); }
    public RecycleBinService(SettingsService settings) { this.settings = settings; }
    public synchronized boolean isAutoEmpty() { return settings.loadRecycleAutoEmpty(); }
    public synchronized void setAutoEmpty(boolean enabled) throws IOException { settings.saveRecycleAutoEmpty(enabled); }

    /** Only an explicit successful pet drop can trigger the opt-in empty operation. */
    public synchronized String receivePetDrop(List<Path> files) throws IOException {
        boolean autoEmpty = isAutoEmpty();
        Result result = recycle(files);
        if (!autoEmpty) return result.summary();
        if (result.moved() == 0 || !result.failures().isEmpty())
            return result.summary() + " 이동하지 못한 파일이 있어 자동 비우기는 실행하지 않았어요.";
        try { empty(true); return result.summary() + " Windows 휴지통 전체를 비웠어요."; }
        catch (IOException | RuntimeException failure) {
            return result.summary() + " 자동 비우기를 완료하지 못했어요: " + failure.getMessage();
        }
    }

    public synchronized BinInfo query() throws IOException { requireWindows(); return shell.query(); }

    public synchronized Result recycle(List<Path> files) throws IOException {
        requireWindows();
        if (files.isEmpty() || files.size() > 256) throw new IOException("한 번에 파일 1~256개를 놓아 주세요.");
        var failures = new ArrayList<Failure>(); int moved = 0;
        for (Path file : new LinkedHashSet<>(files)) {
            try { validateFile(file); shell.recycle(file); moved++; }
            catch (IOException | RuntimeException failure) { failures.add(new Failure(file, failure.getMessage())); }
        }
        return new Result(moved, failures);
    }

    static void validateFile(Path file) throws IOException {
        if (!file.isAbsolute() || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("일반 파일만 보낼 수 있어요. 폴더·심볼릭 링크·없는 파일은 지원하지 않습니다.");
        if (Kernel32.INSTANCE.GetDriveType(file.getRoot().toString()) != 3)
            throw new IOException("로컬 고정 드라이브의 파일만 지원합니다. 네트워크·이동식 드라이브에서는 보내지 않습니다.");
    }

    public synchronized void empty(boolean confirmed) throws IOException {
        if (!confirmed) throw new IllegalArgumentException("휴지통 비우기는 확인 후에만 실행할 수 있습니다.");
        requireWindows(); shell.empty();
    }

    private static void requireWindows() throws IOException {
        if (!Platform.isWindows()) throw new IOException("휴지통 기능은 Windows에서만 사용할 수 있습니다.");
    }
}
