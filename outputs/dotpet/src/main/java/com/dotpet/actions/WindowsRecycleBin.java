package com.dotpet.actions;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Windows Shell recycling, confined to the calling background STA thread. */
final class WindowsRecycleBin {
    private static final Guid.CLSID FILE_OPERATION = new Guid.CLSID("{3AD05575-8857-4850-9277-11B85BDB8E09}");
    private static final Guid.IID OPERATION_IID = new Guid.IID("{947AAB5F-0A5C-4C13-B4D6-4BF7836FC9F8}");
    private static final Guid.IID SHELL_ITEM_IID = new Guid.IID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}");
    // FOFX_ADDUNDORECORD | FOFX_RECYCLEONDELETE | FOFX_EARLYFAILURE | FOF_NOERRORUI |
    // FOF_NOCONFIRMATION | FOF_SILENT | FOF_NO_CONNECTED_ELEMENTS. No elevation or permanent-delete retry.
    static final int RECYCLE_FLAGS = 0x20000000 | 0x00080000 | 0x00100000 | 0x0400 | 0x0010 | 0x0004 | 0x2000;

    public interface Shell extends StdCallLibrary {
        Shell INSTANCE = Native.load("shell32", Shell.class);
        HRESULT SHCreateItemFromParsingName(WString path, Pointer context, Guid.REFIID iid, PointerByReference item);
        HRESULT SHQueryRecycleBinW(WString root, RecycleInfo info);
        HRESULT SHEmptyRecycleBinW(Pointer owner, WString root, int flags);
    }
    @Structure.FieldOrder({"cbSize", "bytes", "items"})
    public static final class RecycleInfo extends Structure {
        public int cbSize;
        public long bytes;
        public long items;
        public RecycleInfo() { cbSize = size(); }
    }

    RecycleBinService.BinInfo query() throws IOException {
        initialize();
        try {
            RecycleInfo info = new RecycleInfo(); check(Shell.INSTANCE.SHQueryRecycleBinW(null, info), "휴지통 조회");
            return new RecycleBinService.BinInfo(info.items, info.bytes);
        } finally { Ole32.INSTANCE.CoUninitialize(); }
    }

    void empty() throws IOException {
        initialize();
        // The caller has already shown its own explicit, cancellable confirmation dialog.
        try { check(Shell.INSTANCE.SHEmptyRecycleBinW(null, null, 1 | 2 | 4), "휴지통 비우기"); }
        finally { Ole32.INSTANCE.CoUninitialize(); }
    }

    void recycle(Path path) throws IOException {
        initialize();
        FileOperation operation = null; Unknown item = null; RecycleGuard guard = null;
        try {
            PointerByReference instance = new PointerByReference();
            check(Ole32.INSTANCE.CoCreateInstance(FILE_OPERATION, null, 1, OPERATION_IID, instance), "휴지통 작업 준비");
            operation = new FileOperation(instance.getValue());
            check(operation.invoke(5, RECYCLE_FLAGS), "휴지통 옵션 설정");
            PointerByReference shellItem = new PointerByReference();
            check(Shell.INSTANCE.SHCreateItemFromParsingName(new WString(path.toString()), null,
                    new Guid.REFIID(SHELL_ITEM_IID), shellItem), "파일 확인");
            item = new Unknown(shellItem.getValue()); guard = new RecycleGuard();
            check(operation.invoke(18, item.getPointer(), guard.instance), "파일 이동 예약");
            HRESULT performed = operation.invoke(21);
            IntByReference aborted = new IntByReference();
            check(operation.invoke(22, aborted), "휴지통 결과 확인");
            if (guard.blocked) throw new IOException("휴지통으로 보낼 수 없어 중단했어요. 일반 삭제는 하지 않습니다.");
            check(performed, "휴지통 이동");
            if (aborted.getValue() != 0 || !guard.recycled)
                throw new IOException("파일을 휴지통으로 옮기지 못했어요. 사용 중이거나 휴지통을 지원하지 않는 위치일 수 있습니다.");
        } finally {
            if (item != null) item.Release();
            if (operation != null) operation.Release();
            Reference.reachabilityFence(guard);
            Ole32.INSTANCE.CoUninitialize();
        }
    }

    private static void initialize() throws IOException { check(Ole32.INSTANCE.CoInitializeEx(null, 2), "Windows 휴지통 초기화"); }
    private static void check(HRESULT result, String action) throws IOException {
        if (result.intValue() < 0) throw new IOException(action + " 실패 (Windows " + String.format("0x%08X", result.intValue()) + ")");
    }
    private static final class FileOperation extends Unknown {
        FileOperation(Pointer pointer) { super(pointer); }
        HRESULT invoke(int slot, Object... arguments) {
            Object[] values = new Object[arguments.length + 1]; values[0] = getPointer();
            System.arraycopy(arguments, 0, values, 1, arguments.length);
            return (HRESULT) _invokeNativeObject(slot, values, HRESULT.class);
        }
    }

    /** Refuses any PreDeleteItem which the Shell has not marked for recycling. */
    static final class RecycleGuard {
        private static final int E_ABORT = 0x80004004, E_NOINTERFACE = 0x80004002;
        private static final byte[] UNKNOWN = new Guid.IID("{00000000-0000-0000-C000-000000000046}").getPointer().getByteArray(0, 16);
        private static final byte[] SINK = new Guid.IID("{04B0F1A7-9490-44BC-96E1-4296A31252E2}").getPointer().getByteArray(0, 16);
        private final AtomicInteger references = new AtomicInteger(1);
        final Memory instance = new Memory(Native.POINTER_SIZE);
        private final Memory vtable = new Memory(19L * Native.POINTER_SIZE);
        private final Callback[] callbacks;
        volatile boolean blocked, recycled;

        public interface Query extends StdCallLibrary.StdCallCallback { int call(Pointer self, Pointer iid, Pointer result); }
        public interface Ref extends StdCallLibrary.StdCallCallback { int call(Pointer self); }
        public interface None extends StdCallLibrary.StdCallCallback { int call(Pointer self); }
        public interface Finish extends StdCallLibrary.StdCallCallback { int call(Pointer self, int result); }
        public interface Name extends StdCallLibrary.StdCallCallback { int call(Pointer self, int flags, Pointer item, Pointer name); }
        public interface PostName extends StdCallLibrary.StdCallCallback { int call(Pointer self, int flags, Pointer item, Pointer name, int result, Pointer created); }
        public interface Transfer extends StdCallLibrary.StdCallCallback { int call(Pointer self, int flags, Pointer item, Pointer destination, Pointer name); }
        public interface PostTransfer extends StdCallLibrary.StdCallCallback { int call(Pointer self, int flags, Pointer item, Pointer destination, Pointer name, int result, Pointer created); }
        public interface Delete extends StdCallLibrary.StdCallCallback { int call(Pointer self, int flags, Pointer item); }
        public interface PostDelete extends StdCallLibrary.StdCallCallback { int call(Pointer self, int flags, Pointer item, int result, Pointer created); }
        public interface NewItem extends StdCallLibrary.StdCallCallback { int call(Pointer self, int flags, Pointer destination, Pointer name, Pointer template, int attributes, int result, Pointer created); }
        public interface Progress extends StdCallLibrary.StdCallCallback { int call(Pointer self, int total, int done); }

        RecycleGuard() {
            callbacks = new Callback[]{
                (Query) (self, iid, result) -> {
                    byte[] bytes = iid.getByteArray(0, 16);
                    if (Arrays.equals(bytes, UNKNOWN) || Arrays.equals(bytes, SINK)) {
                        result.setPointer(0, instance); references.incrementAndGet(); return 0;
                    }
                    result.setPointer(0, null); return E_NOINTERFACE;
                },
                (Ref) self -> references.incrementAndGet(), (Ref) self -> references.decrementAndGet(),
                (None) self -> 0, (Finish) (self, result) -> 0,
                (Name) (self, flags, item, name) -> E_ABORT,
                (PostName) (self, flags, item, name, result, created) -> 0,
                (Transfer) (self, flags, item, destination, name) -> E_ABORT,
                (PostTransfer) (self, flags, item, destination, name, result, created) -> 0,
                (Transfer) (self, flags, item, destination, name) -> E_ABORT,
                (PostTransfer) (self, flags, item, destination, name, result, created) -> 0,
                (Delete) (self, flags, item) -> beforeDelete(flags),
                (PostDelete) (self, flags, item, result, created) -> { recycled = result >= 0 && created != null; return 0; },
                (Name) (self, flags, destination, name) -> E_ABORT,
                (NewItem) (self, flags, destination, name, template, attributes, result, created) -> 0,
                (Progress) (self, total, done) -> 0, (None) self -> 0, (None) self -> 0, (None) self -> 0
            };
            for (int i = 0; i < callbacks.length; i++) vtable.setPointer((long) i * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(callbacks[i]));
            instance.setPointer(0, vtable);
        }

        int beforeDelete(int flags) {
            if ((flags & 0x80) == 0 || Thread.currentThread().isInterrupted()) { blocked = true; return E_ABORT; }
            return 0;
        }
    }
}
