package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.exteraless.glyph.GlyphConfig;
import app.exteraless.glyph.GlyphController;
import app.exteraless.glyph.GlyphDevice;
import app.exteraless.glyph.GlyphEvent;
import app.exteraless.glyph.GlyphRule;
import app.exteraless.glyph.GlyphRules;
import app.exteraless.glyph.GlyphZone;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Editor of one glyph rule: the rule of an event type or the rule of a chat. */
public class OpenExteraGlyphRuleActivity extends BaseNekoSettingsActivity {

    private static final String ARG_EVENT = "event";
    private static final String ARG_DIALOG_ID = "dialog_id";

    /** Null when the screen edits a chat rule. */
    private GlyphEvent event;
    private long dialogId;

    private boolean ruleEnabled;
    /** Zones ticked on screen. A stored empty set shows up as all of them. */
    private final Set<String> checkedZones = new LinkedHashSet<>();
    private List<GlyphZone> zones;

    private int enabledRow;
    private int zonesDividerRow;
    private int zonesHeaderRow;
    private int zonesStartRow;
    private int zonesEndRow;
    private int actionsDividerRow;
    private int previewRow;
    private int deleteRow;
    private int infoRow;

    public static OpenExteraGlyphRuleActivity forEvent(GlyphEvent event) {
        Bundle args = new Bundle();
        args.putString(ARG_EVENT, event.name());
        return new OpenExteraGlyphRuleActivity(args);
    }

    public static OpenExteraGlyphRuleActivity forChat(long dialogId) {
        Bundle args = new Bundle();
        args.putLong(ARG_DIALOG_ID, dialogId);
        return new OpenExteraGlyphRuleActivity(args);
    }

    public OpenExteraGlyphRuleActivity(Bundle args) {
        super();
        arguments = args;
    }

    /** Chat title for a rule row, or the raw id when no account knows the chat. */
    static String chatName(int account, long dialogId) {
        String name = cachedChatName(account, dialogId);
        // Chat rules are shared by all accounts, so the chat may belong to another one.
        for (int a = 0; name.isEmpty() && a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a != account && UserConfig.getInstance(a).isClientActivated()) {
                name = cachedChatName(a, dialogId);
            }
        }
        return name.isEmpty() ? String.valueOf(dialogId) : name;
    }

    private static String cachedChatName(int account, long dialogId) {
        MessagesController controller = MessagesController.getInstance(account);
        String name;
        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat chat = controller.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            name = chat == null ? null : DialogObject.getName(controller.getUser(chat.user_id));
        } else {
            name = DialogObject.getName(controller.getUserOrChat(dialogId));
        }
        return name == null ? "" : name;
    }

    @Override
    public boolean onFragmentCreate() {
        GlyphConfig.init();
        String eventName = arguments == null ? null : arguments.getString(ARG_EVENT);
        dialogId = arguments == null ? 0 : arguments.getLong(ARG_DIALOG_ID, 0);
        GlyphRule rule;
        if (eventName != null) {
            try {
                event = GlyphEvent.valueOf(eventName);
            } catch (IllegalArgumentException e) {
                return false;
            }
            rule = GlyphRules.eventRule(event);
        } else if (dialogId != 0) {
            rule = GlyphRules.chatRule(dialogId);
            if (rule == null) {
                rule = GlyphRule.DEFAULT;
                GlyphRules.setChatRule(dialogId, rule);
            }
        } else {
            return false;
        }

        zones = GlyphDevice.current().zones;
        ruleEnabled = rule.enabled;
        // Ids from another phone model can arrive with a settings backup.
        for (GlyphZone zone : zones) {
            if (rule.zones.contains(zone.id)) {
                checkedZones.add(zone.id);
            }
        }
        if (checkedZones.isEmpty()) {
            checkAllZones();
        }
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        enabledRow = addRow();
        if (zones != null && !zones.isEmpty()) {
            zonesDividerRow = addRow();
            zonesHeaderRow = addRow();
            zonesStartRow = rowCount;
            rowCount += zones.size();
            zonesEndRow = rowCount;
        } else {
            zonesDividerRow = zonesHeaderRow = zonesStartRow = zonesEndRow = -1;
        }
        actionsDividerRow = addRow();
        previewRow = addRow();
        deleteRow = event == null ? addRow() : -1;
        infoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        if (event == null) {
            return chatName(currentAccount, dialogId);
        }
        switch (event) {
            case CALL:
                return getString(R.string.OEGlyphEventCall);
            case RECORDING:
                return getString(R.string.OEGlyphEventRecording);
            case MESSAGE:
            default:
                return getString(R.string.OEGlyphEventMessage);
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == enabledRow) {
            ruleEnabled = !ruleEnabled;
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(ruleEnabled);
            }
            save();
        } else if (position >= zonesStartRow && position < zonesEndRow) {
            String id = zones.get(position - zonesStartRow).id;
            if (checkedZones.contains(id)) {
                if (checkedZones.size() == 1) {
                    BulletinFactory.of(this)
                            .createSimpleBulletin(R.raw.info, getString(R.string.OEGlyphRuleLastZone))
                            .show();
                    return;
                }
                checkedZones.remove(id);
            } else {
                checkedZones.add(id);
            }
            if (view instanceof TextCheckbox2Cell) {
                ((TextCheckbox2Cell) view).setChecked(checkedZones.contains(id));
            }
            save();
        } else if (position == previewRow) {
            // A chat rule is previewed as a message, its most common use.
            GlyphController.getInstance().preview(storedZones(),
                    event != null ? event : GlyphEvent.MESSAGE);
        } else if (position == deleteRow) {
            GlyphRules.removeChatRule(dialogId);
            finishFragment();
        }
    }

    private void checkAllZones() {
        for (GlyphZone zone : zones) {
            checkedZones.add(zone.id);
        }
    }

    /** Zones as they are stored: in layout order, with "all of them" as an empty set. */
    private Set<String> storedZones() {
        Set<String> stored = new LinkedHashSet<>();
        if (checkedZones.size() < zones.size()) {
            for (GlyphZone zone : zones) {
                if (checkedZones.contains(zone.id)) {
                    stored.add(zone.id);
                }
            }
        }
        return stored;
    }

    private void save() {
        GlyphRule rule = new GlyphRule(ruleEnabled, storedZones());
        if (event != null) {
            GlyphRules.setEventRule(event, rule);
        } else {
            GlyphRules.setChatRule(dialogId, rule);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getAdapterPosition() == previewRow && !GlyphConfig.enabled()) {
                return false;
            }
            return super.isEnabled(holder);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(getString(R.string.OEGlyphRuleGlyphs));
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck(getString(R.string.OEGlyphRuleEnabled), ruleEnabled, false);
                    break;
                }
                case TYPE_CHECKBOX: {
                    TextCheckbox2Cell cell = (TextCheckbox2Cell) holder.itemView;
                    GlyphZone zone = zones.get(position - zonesStartRow);
                    cell.setTextAndCheck(formatString(R.string.OEGlyphZone, zone.label),
                            checkedZones.contains(zone.id), position + 1 < zonesEndRow);
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == previewRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon,
                                Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.OEGlyphPreview),
                                R.drawable.deproko_baseline_lamp_filled_24, deleteRow != -1);
                        cell.setEnabled(GlyphConfig.enabled(), false);
                    } else if (position == deleteRow) {
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                        cell.setTextAndIcon(getString(R.string.OEGlyphRuleDelete),
                                R.drawable.msg_delete, false);
                        cell.setEnabled(true, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(getString(event != null
                            ? R.string.OEGlyphRuleInfoEvent : R.string.OEGlyphRuleInfoChat));
                    cell.setBackground(Theme.getThemedDrawable(mContext,
                            R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == enabledRow) {
                return TYPE_CHECK;
            } else if (position == zonesHeaderRow) {
                return TYPE_HEADER;
            } else if (position >= zonesStartRow && position < zonesEndRow) {
                return TYPE_CHECKBOX;
            } else if (position == previewRow || position == deleteRow) {
                return TYPE_TEXT;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
        }
    }
}
