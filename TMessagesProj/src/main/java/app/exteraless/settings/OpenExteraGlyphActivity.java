package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.exteraless.glyph.GlyphConfig;
import app.exteraless.glyph.GlyphController;
import app.exteraless.glyph.GlyphDevice;
import app.exteraless.glyph.GlyphEvent;
import app.exteraless.glyph.GlyphRule;
import app.exteraless.glyph.GlyphRules;
import app.exteraless.glyph.GlyphZone;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/** Nothing Glyph settings: the master switch and the rules that pick the glyphs. */
public class OpenExteraGlyphActivity extends BaseNekoSettingsActivity {

    private int glyphHeaderRow;
    private int enableRow;
    private int enableDividerRow;
    private int eventsHeaderRow;
    private int eventMessageRow;
    private int eventCallRow;
    private int eventRecordingRow;
    private int eventsDividerRow;
    private int chatsHeaderRow;
    private int addChatRow;
    private int chatsStartRow;
    private int chatsEndRow;
    private int chatsDividerRow;
    private int screenOffRow;
    private int infoRow;

    /** Chats that have a rule, sorted by name. */
    private final List<Long> chatIds = new ArrayList<>();
    private final Map<Long, String> chatNames = new HashMap<>();

    public OpenExteraGlyphActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        GlyphConfig.init();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        // The rule editor changes the rules while this screen is in the back stack.
        updateRows();
        super.onResume();
    }

    // Row keys are joined with the search prefix to find the row title, so
    // "eventCall" is found in settings search as OEGlyphEventCall.
    @Override
    protected void updateRows() {
        super.updateRows();

        glyphHeaderRow = addRow("glyphHeader");
        enableRow = addRow("enable");
        enableDividerRow = addRow();

        eventsHeaderRow = addRow("eventsHeader");
        eventMessageRow = addRow("eventMessage");
        eventCallRow = addRow("eventCall");
        eventRecordingRow = addRow("eventRecording");
        eventsDividerRow = addRow();

        loadChats();
        chatsHeaderRow = addRow("chatsHeader");
        addChatRow = addRow("addChat");
        chatsStartRow = rowCount;
        rowCount += chatIds.size();
        chatsEndRow = rowCount;
        chatsDividerRow = addRow();

        screenOffRow = addRow("screenOff");
        infoRow = addRow();
    }

    private void loadChats() {
        chatIds.clear();
        chatNames.clear();
        for (long dialogId : GlyphRules.chatRules().keySet()) {
            chatIds.add(dialogId);
            chatNames.put(dialogId, OpenExteraGlyphRuleActivity.chatName(currentAccount, dialogId));
        }
        chatIds.sort((a, b) -> {
            int byName = chatNames.get(a).compareToIgnoreCase(chatNames.get(b));
            return byName != 0 ? byName : Long.compare(a, b);
        });
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OEGlyphTitle);
    }

    @Override
    public int getSearchGuid() {
        return 25000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.deproko_baseline_lamp_filled_24;
    }

    @Override
    public String getSearchPrefix() {
        return "OEGlyph";
    }

    @Override
    protected String getKey() {
        return "exteraless_glyph";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == enableRow) {
            boolean enabled = GlyphConfig.enabled.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            if (enabled) {
                GlyphController.getInstance().init();
            } else {
                GlyphController.getInstance().shutdown();
            }
            // Rebind everything below the switch, it follows its state.
            listAdapter.notifyItemRangeChanged(enableRow + 1, rowCount - enableRow - 1);
        } else if (position == eventMessageRow) {
            presentFragment(OpenExteraGlyphRuleActivity.forEvent(GlyphEvent.MESSAGE));
        } else if (position == eventCallRow) {
            presentFragment(OpenExteraGlyphRuleActivity.forEvent(GlyphEvent.CALL));
        } else if (position == eventRecordingRow) {
            presentFragment(OpenExteraGlyphRuleActivity.forEvent(GlyphEvent.RECORDING));
        } else if (position == addChatRow) {
            pickChat();
        } else if (position >= chatsStartRow && position < chatsEndRow) {
            presentFragment(OpenExteraGlyphRuleActivity.forChat(chatIds.get(position - chatsStartRow)));
        } else if (position == screenOffRow) {
            boolean enabled = GlyphConfig.screenOffOnly.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        }
    }

    private void pickChat() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        // A rule is useful for chats the user can only read, such as channels.
        args.putBoolean("checkCanWrite", false);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((picker, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids.isEmpty()) {
                return true;
            }
            // The editor replaces the picker and creates the rule if the chat has none.
            picker.presentFragment(OpenExteraGlyphRuleActivity.forChat(dids.get(0).dialogId), true);
            return true;
        });
        presentFragment(fragment);
    }

    /** Short description of a rule, shown next to its row. */
    private static String ruleSummary(GlyphRule rule) {
        if (!rule.enabled) {
            return getString(R.string.OEGlyphRuleOff);
        }
        GlyphDevice device = GlyphDevice.current();
        if (device.kind != GlyphDevice.Kind.STRIP) {
            // The Glyph Matrix has no zones to list.
            return "";
        }
        List<String> labels = new ArrayList<>();
        for (GlyphZone zone : device.zones) {
            if (rule.zones.contains(zone.id)) {
                labels.add(zone.label);
            }
        }
        if (labels.isEmpty() || labels.size() == device.zones.size()) {
            return getString(R.string.OEGlyphRuleAll);
        }
        return TextUtils.join(", ", labels);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getAdapterPosition() != enableRow && !GlyphConfig.enabled()) {
                return false;
            }
            return super.isEnabled(holder);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            // Cells are recycled, so the dimmed state is set on every bind.
            boolean active = GlyphConfig.enabled();
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == glyphHeaderRow) {
                        cell.setText(getString(R.string.OEGlyphTitle));
                    } else if (position == eventsHeaderRow) {
                        cell.setText(getString(R.string.OEGlyphEvents));
                    } else if (position == chatsHeaderRow) {
                        cell.setText(getString(R.string.OEGlyphChats));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setEnabled(position == enableRow || active, null);
                    if (position == enableRow) {
                        cell.setTextAndCheck(getString(R.string.OEGlyphEnable),
                                GlyphConfig.enabled(), false);
                    } else if (position == screenOffRow) {
                        cell.setTextAndCheck(getString(R.string.OEGlyphScreenOff),
                                GlyphConfig.screenOffOnly(), false);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == eventMessageRow) {
                        cell.setTextAndValue(getString(R.string.OEGlyphEventMessage),
                                ruleSummary(GlyphRules.eventRule(GlyphEvent.MESSAGE)), true);
                    } else if (position == eventCallRow) {
                        cell.setTextAndValue(getString(R.string.OEGlyphEventCall),
                                ruleSummary(GlyphRules.eventRule(GlyphEvent.CALL)), true);
                    } else if (position == eventRecordingRow) {
                        cell.setTextAndValue(getString(R.string.OEGlyphEventRecording),
                                ruleSummary(GlyphRules.eventRule(GlyphEvent.RECORDING)), false);
                    } else if (position >= chatsStartRow && position < chatsEndRow) {
                        long dialogId = chatIds.get(position - chatsStartRow);
                        GlyphRule rule = GlyphRules.chatRule(dialogId);
                        cell.setTextAndValue(chatNames.get(dialogId),
                                rule == null ? "" : ruleSummary(rule), position + 1 < chatsEndRow);
                    }
                    cell.setEnabled(active, null);
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon,
                            Theme.key_windowBackgroundWhiteBlueText2);
                    cell.setTextAndIcon(getString(R.string.OEGlyphAddChat), R.drawable.msg_add,
                            chatsEndRow > chatsStartRow);
                    cell.setEnabled(active, false);
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(getString(R.string.OEGlyphInfo));
                    cell.setBackground(Theme.getThemedDrawable(mContext,
                            R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == glyphHeaderRow || position == eventsHeaderRow
                    || position == chatsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == enableRow || position == screenOffRow) {
                return TYPE_CHECK;
            } else if (position == eventMessageRow || position == eventCallRow
                    || position == eventRecordingRow
                    || position >= chatsStartRow && position < chatsEndRow) {
                return TYPE_SETTINGS;
            } else if (position == addChatRow) {
                return TYPE_TEXT;
            } else if (position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
        }
    }
}
