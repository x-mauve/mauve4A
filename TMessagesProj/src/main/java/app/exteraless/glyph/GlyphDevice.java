package app.exteraless.glyph;

import android.os.Build;

import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Glyph hardware of the phone the app runs on. */
public final class GlyphDevice {

    public enum Kind {
        /** Individually addressable LED zones (Glyph Interface, Glyph Bar). */
        STRIP,
        /** Glyph Matrix. */
        MATRIX,
        NONE
    }

    private static final GlyphDevice UNSUPPORTED =
            new GlyphDevice(Kind.NONE, null, 0, Collections.emptyList());

    private static volatile GlyphDevice current;

    public final Kind kind;
    /** One of the {@code Glyph.DEVICE_*} constants, null for {@link Kind#NONE}. */
    public final String sdkDevice;
    /** Length of the frame the SDK expects, 0 for anything but {@link Kind#STRIP}. */
    public final int channelCount;
    public final List<GlyphZone> zones;

    private GlyphDevice(Kind kind, String sdkDevice, int channelCount, List<GlyphZone> zones) {
        this.kind = kind;
        this.sdkDevice = sdkDevice;
        this.channelCount = channelCount;
        this.zones = Collections.unmodifiableList(zones);
    }

    public static GlyphDevice current() {
        GlyphDevice local = current;
        if (local == null) {
            synchronized (GlyphDevice.class) {
                local = current;
                if (local == null) {
                    local = current = detect();
                }
            }
        }
        return local;
    }

    /** Ids of all zones, in layout order. */
    public Set<String> zoneIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (GlyphZone zone : zones) {
            ids.add(zone.id);
        }
        return ids;
    }

    private static GlyphDevice detect() {
        if (!"nothing".equalsIgnoreCase(Build.MANUFACTURER)) {
            return UNSUPPORTED;
        }
        try {
            // Matrix phones first: the strip checks below must never claim them.
            if (Common.is23112()) {
                return matrix(Glyph.DEVICE_23112);
            }
            if (Common.is25111p()) {
                return matrix(Glyph.DEVICE_25111p);
            }
            if (Common.is20111()) {
                return phone1();
            }
            if (Common.is22111()) {
                return phone2();
            }
            if (Common.is23111()) {
                return phone2a(Glyph.DEVICE_23111);
            }
            if (Common.is23113()) {
                return phone2a(Glyph.DEVICE_23113);
            }
            if (Common.is24111()) {
                return phone3a();
            }
            if (Common.is25111()) {
                return phone4a();
            }
            if (Common.is25131()) {
                return phone4b();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return UNSUPPORTED;
    }

    private static GlyphDevice matrix(String sdkDevice) {
        return new GlyphDevice(Kind.MATRIX, sdkDevice, 0, Collections.emptyList());
    }

    private static GlyphDevice strip(String sdkDevice, int channelCount, List<GlyphZone> zones) {
        return new GlyphDevice(Kind.STRIP, sdkDevice, channelCount, zones);
    }

    private static GlyphDevice phone1() {
        List<GlyphZone> zones = new ArrayList<>();
        zones.add(new GlyphZone("A", Glyph.Code_20111.A1));
        zones.add(new GlyphZone("B", Glyph.Code_20111.B1));
        zones.add(new GlyphZone("C",
                Glyph.Code_20111.C1, Glyph.Code_20111.C2, Glyph.Code_20111.C3, Glyph.Code_20111.C4));
        zones.add(new GlyphZone("D",
                Glyph.Code_20111.D1_1, Glyph.Code_20111.D1_2, Glyph.Code_20111.D1_3, Glyph.Code_20111.D1_4,
                Glyph.Code_20111.D1_5, Glyph.Code_20111.D1_6, Glyph.Code_20111.D1_7, Glyph.Code_20111.D1_8));
        zones.add(new GlyphZone("E", Glyph.Code_20111.E1));
        return strip(Glyph.DEVICE_20111, 15, zones);
    }

    private static GlyphDevice phone2() {
        List<GlyphZone> zones = new ArrayList<>();
        zones.add(new GlyphZone("A1", Glyph.Code_22111.A1));
        zones.add(new GlyphZone("A2", Glyph.Code_22111.A2));
        zones.add(new GlyphZone("B", Glyph.Code_22111.B1));
        zones.add(new GlyphZone("C1",
                Glyph.Code_22111.C1_1, Glyph.Code_22111.C1_2, Glyph.Code_22111.C1_3, Glyph.Code_22111.C1_4,
                Glyph.Code_22111.C1_5, Glyph.Code_22111.C1_6, Glyph.Code_22111.C1_7, Glyph.Code_22111.C1_8,
                Glyph.Code_22111.C1_9, Glyph.Code_22111.C1_10, Glyph.Code_22111.C1_11, Glyph.Code_22111.C1_12,
                Glyph.Code_22111.C1_13, Glyph.Code_22111.C1_14, Glyph.Code_22111.C1_15, Glyph.Code_22111.C1_16));
        zones.add(new GlyphZone("C2", Glyph.Code_22111.C2));
        zones.add(new GlyphZone("C3", Glyph.Code_22111.C3));
        zones.add(new GlyphZone("C4", Glyph.Code_22111.C4));
        zones.add(new GlyphZone("C5", Glyph.Code_22111.C5));
        zones.add(new GlyphZone("C6", Glyph.Code_22111.C6));
        zones.add(new GlyphZone("D",
                Glyph.Code_22111.D1_1, Glyph.Code_22111.D1_2, Glyph.Code_22111.D1_3, Glyph.Code_22111.D1_4,
                Glyph.Code_22111.D1_5, Glyph.Code_22111.D1_6, Glyph.Code_22111.D1_7, Glyph.Code_22111.D1_8));
        zones.add(new GlyphZone("E", Glyph.Code_22111.E1));
        return strip(Glyph.DEVICE_22111, 33, zones);
    }

    /** Phone (2a) and (2a) Plus share one layout and one set of channel codes. */
    private static GlyphDevice phone2a(String sdkDevice) {
        List<GlyphZone> zones = new ArrayList<>();
        zones.add(new GlyphZone("A", Glyph.Code_23111.A));
        zones.add(new GlyphZone("B", Glyph.Code_23111.B));
        zones.add(new GlyphZone("C",
                Glyph.Code_23111.C_1, Glyph.Code_23111.C_2, Glyph.Code_23111.C_3, Glyph.Code_23111.C_4,
                Glyph.Code_23111.C_5, Glyph.Code_23111.C_6, Glyph.Code_23111.C_7, Glyph.Code_23111.C_8,
                Glyph.Code_23111.C_9, Glyph.Code_23111.C_10, Glyph.Code_23111.C_11, Glyph.Code_23111.C_12,
                Glyph.Code_23111.C_13, Glyph.Code_23111.C_14, Glyph.Code_23111.C_15, Glyph.Code_23111.C_16,
                Glyph.Code_23111.C_17, Glyph.Code_23111.C_18, Glyph.Code_23111.C_19, Glyph.Code_23111.C_20,
                Glyph.Code_23111.C_21, Glyph.Code_23111.C_22, Glyph.Code_23111.C_23, Glyph.Code_23111.C_24));
        return strip(sdkDevice, 26, zones);
    }

    /** Phone (3a) and (3a) Pro report the same SDK device. */
    private static GlyphDevice phone3a() {
        List<GlyphZone> zones = new ArrayList<>();
        zones.add(new GlyphZone("A",
                Glyph.Code_24111.A_1, Glyph.Code_24111.A_2, Glyph.Code_24111.A_3, Glyph.Code_24111.A_4,
                Glyph.Code_24111.A_5, Glyph.Code_24111.A_6, Glyph.Code_24111.A_7, Glyph.Code_24111.A_8,
                Glyph.Code_24111.A_9, Glyph.Code_24111.A_10, Glyph.Code_24111.A_11));
        zones.add(new GlyphZone("B",
                Glyph.Code_24111.B_1, Glyph.Code_24111.B_2, Glyph.Code_24111.B_3, Glyph.Code_24111.B_4,
                Glyph.Code_24111.B_5));
        zones.add(new GlyphZone("C",
                Glyph.Code_24111.C_1, Glyph.Code_24111.C_2, Glyph.Code_24111.C_3, Glyph.Code_24111.C_4,
                Glyph.Code_24111.C_5, Glyph.Code_24111.C_6, Glyph.Code_24111.C_7, Glyph.Code_24111.C_8,
                Glyph.Code_24111.C_9, Glyph.Code_24111.C_10, Glyph.Code_24111.C_11, Glyph.Code_24111.C_12,
                Glyph.Code_24111.C_13, Glyph.Code_24111.C_14, Glyph.Code_24111.C_15, Glyph.Code_24111.C_16,
                Glyph.Code_24111.C_17, Glyph.Code_24111.C_18, Glyph.Code_24111.C_19, Glyph.Code_24111.C_20));
        return strip(Glyph.DEVICE_24111, 36, zones);
    }

    private static GlyphDevice phone4a() {
        List<GlyphZone> zones = new ArrayList<>();
        zones.add(new GlyphZone("1", Glyph.Code_25111.A_1));
        zones.add(new GlyphZone("2", Glyph.Code_25111.A_2));
        zones.add(new GlyphZone("3", Glyph.Code_25111.A_3));
        zones.add(new GlyphZone("4", Glyph.Code_25111.A_4));
        zones.add(new GlyphZone("5", Glyph.Code_25111.A_5));
        zones.add(new GlyphZone("6", Glyph.Code_25111.A_6));
        return strip(Glyph.DEVICE_25111, 6, zones);
    }

    private static GlyphDevice phone4b() {
        List<GlyphZone> zones = new ArrayList<>();
        zones.add(new GlyphZone("1", Glyph.Code_25131.A_1));
        zones.add(new GlyphZone("2", Glyph.Code_25131.A_2));
        zones.add(new GlyphZone("3", Glyph.Code_25131.A_3));
        zones.add(new GlyphZone("4", Glyph.Code_25131.A_4));
        return strip(Glyph.DEVICE_25131, 4, zones);
    }
}
