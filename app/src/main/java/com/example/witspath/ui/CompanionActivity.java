package com.example.witspath.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.witspath.R;
import com.example.witspath.companion.CompanionClient;
import com.example.witspath.companion.CloudTranscriber;
import com.example.witspath.companion.CompanionEndpoint;
import com.example.witspath.companion.SpeechRecorder;
import com.example.witspath.companion.MarkdownLite;
import com.example.witspath.companion.CompanionLanguage;
import com.example.witspath.companion.CompanionReply;
import com.example.witspath.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Team Wavelets - WitsPath
 * WhatsApp-style chat screen for AI Companion with suggestion chips, typing indicator, route cards, and speech/audio support.
 */
public class CompanionActivity extends BaseActivity {

    public static final class ChatItem {
        public enum Type { DAY_SEPARATOR, WELCOME, USER, COMPANION, TYPING, ERROR }
        final Type type;
        final String text;
        final CompanionReply reply;
        final String timestamp;

        ChatItem(Type type, String text, CompanionReply reply) {
            this.type = type;
            this.text = text;
            this.reply = reply;
            this.timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        }
    }

    private final List<ChatItem> items = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView messages;
    private EditText input;
    private MaterialButton sendMicButton;
    private MaterialToolbar toolbar;
    private TextView subtitle;

    private CompanionClient client;
    private boolean waiting;
    private boolean readAloudEnabled = false;
    private CompanionLanguage selectedLanguage = null;

    private TextToSpeech tts;
    private boolean ttsReady;
    private String speakingId = null;

    private SpeechRecognizer recognizer;
    private final SpeechRecorder recorder = new SpeechRecorder();
    private final CloudTranscriber transcriber = new CloudTranscriber();
    /** True once the backend reports that its speech-to-text key is set. Until then the phone recogniser is used. */
    private boolean cloudVoiceAvailable;
    private boolean cloudRecording;
    private boolean listening;
    private String listeningLang = "en";

    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startListening();
                else Toast.makeText(this, R.string.companion_voice_unavailable, Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion);

        client = newClient();
        checkCloudVoice();

        toolbar = findViewById(R.id.companionToolbar);
        subtitle = findViewById(R.id.companionSubtitle);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_companion);
        toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);
        toolbar.getMenu().findItem(R.id.action_debug_endpoint).setVisible(CompanionEndpoint.overrideAllowed(this));

        messages = findViewById(R.id.companionMessages);
        input = findViewById(R.id.companionInput);
        sendMicButton = findViewById(R.id.companionSendMicButton);

        View root = findViewById(R.id.companionRoot);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                Insets sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int bottomPadding = Math.max(imeInsets.bottom, sysInsets.bottom);
                v.setPadding(0, sysInsets.top, 0, bottomPadding);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        adapter = new ChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        messages.setLayoutManager(lm);
        messages.setAdapter(adapter);

        setupTts();

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateMicSendButton();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        sendMicButton.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                sendTyped();
            } else {
                onMicClicked();
            }
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped();
                return true;
            }
            return false;
        });

        initConversation();
    }

    private void setSubtitle(int resId) {
        if (subtitle != null) subtitle.setText(resId);
    }

    private void initConversation() {
        items.clear();
        items.add(new ChatItem(ChatItem.Type.DAY_SEPARATOR, getString(R.string.companion_today), null));
        items.add(new ChatItem(ChatItem.Type.WELCOME, getString(R.string.companion_welcome), null));
        adapter.notifyDataSetChanged();
        setSubtitle(R.string.companion_online);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_companion, menu);
        menu.findItem(R.id.action_read_aloud).setChecked(readAloudEnabled);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_language) {
            showLanguageBottomSheet();
            return true;
        } else if (id == R.id.action_read_aloud) {
            readAloudEnabled = !readAloudEnabled;
            item.setChecked(readAloudEnabled);
            if (!readAloudEnabled) stopSpeaking();
            return true;
        } else if (id == R.id.action_new_chat) {
            client.newConversation();
            initConversation();
            return true;
        } else if (id == R.id.action_share_chat) {
            shareChat();
            return true;
        } else if (id == R.id.action_debug_endpoint) {
            showDebugEndpointDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private CompanionClient newClient() {
        Prefs prefs = new Prefs(this);
        return new CompanionClient(CompanionEndpoint.resolve(this, prefs), prefs);
    }

    /** Debug builds only: paste the current tunnel address without rebuilding the app. */
    private void showDebugEndpointDialog() {
        if (!CompanionEndpoint.overrideAllowed(this)) return;
        Prefs prefs = new Prefs(this);
        EditText field = new EditText(this);
        field.setHint(R.string.companion_debug_endpoint_hint);
        field.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        field.setSingleLine(true);
        field.setText(prefs.getString(Prefs.KEY_DEBUG_COMPANION_ENDPOINT, ""));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(pad, pad / 2, pad, 0);
        holder.addView(field);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.companion_debug_endpoint_title)
                .setMessage(R.string.companion_debug_endpoint_help)
                .setView(holder)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.companion_debug_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = field.getText().toString().trim();
            if (typed.isEmpty()) {
                prefs.setString(Prefs.KEY_DEBUG_COMPANION_ENDPOINT, "");
                Toast.makeText(this, R.string.companion_debug_endpoint_reset, Toast.LENGTH_SHORT).show();
            } else {
                String url = CompanionEndpoint.normalise(typed);
                if (url == null) {
                    field.setError(getString(R.string.companion_debug_endpoint_invalid));
                    return;
                }
                prefs.setString(Prefs.KEY_DEBUG_COMPANION_ENDPOINT, url);
                Toast.makeText(this, R.string.companion_debug_endpoint_saved, Toast.LENGTH_SHORT).show();
            }
            client.shutdown();
            client = newClient();
            checkCloudVoice();
            initConversation();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showLanguageBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_companion_language, null);
        dialog.setContentView(view);

        LinearLayout listContainer = view.findViewById(R.id.languageListContainer);
        listContainer.removeAllViews();

        addLanguageOption(dialog, listContainer, getString(R.string.companion_language_auto), null);
        for (CompanionLanguage l : CompanionLanguage.values()) {
            String label = getString(l.full ? R.string.companion_lang_full : R.string.companion_lang_limited, l.displayName);
            addLanguageOption(dialog, listContainer, label, l);
        }

        dialog.show();
    }

    private void addLanguageOption(BottomSheetDialog dialog, LinearLayout container, String label, CompanionLanguage lang) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_companion_language_option, container, false);
        TextView tv = row.findViewById(R.id.languageOptionText);
        tv.setText(label);
        boolean selected = (selectedLanguage == lang);
        row.setBackgroundColor(selected ? getResources().getColor(R.color.colorPlateRaised, getTheme()) : Color.TRANSPARENT);
        row.setOnClickListener(v -> {
            selectedLanguage = lang;
            dialog.dismiss();
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
        });
        container.addView(row);
    }

    private void sendTyped() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || waiting) return;
        input.setText("");
        send(text, "text", null);
    }

    private void send(String text, String mode, String inputLang) {
        stopSpeaking();
        waiting = true;
        setSubtitle(R.string.companion_typing);

        items.add(new ChatItem(ChatItem.Type.USER, text, null));
        int typingIndex = items.size();
        items.add(new ChatItem(ChatItem.Type.TYPING, "", null));
        adapter.notifyItemRangeInserted(items.size() - 2, 2);
        messages.scrollToPosition(items.size() - 1);

        String prefCode = selectedLanguage == null ? null : selectedLanguage.code;
        client.send(text, mode, inputLang, prefCode, new CompanionClient.Callback() {
            @Override
            public void onReply(CompanionReply reply) {
                waiting = false;
                setSubtitle(R.string.companion_online);
                if (typingIndex < items.size() && items.get(typingIndex).type == ChatItem.Type.TYPING) {
                    items.remove(typingIndex);
                    adapter.notifyItemRemoved(typingIndex);
                }
                items.add(new ChatItem(ChatItem.Type.COMPANION, reply.reply, reply));
                adapter.notifyItemInserted(items.size() - 1);
                messages.scrollToPosition(items.size() - 1);
                if (readAloudEnabled) speak(reply);
            }

            @Override
            public void onUnavailable() {
                waiting = false;
                setSubtitle(R.string.companion_online);
                if (typingIndex < items.size() && items.get(typingIndex).type == ChatItem.Type.TYPING) {
                    items.remove(typingIndex);
                    adapter.notifyItemRemoved(typingIndex);
                }
                items.add(new ChatItem(ChatItem.Type.ERROR, getString(R.string.companion_unavailable_message), null));
                adapter.notifyItemInserted(items.size() - 1);
                messages.scrollToPosition(items.size() - 1);
            }
        });
    }

    private void onMicClicked() {
        if (listening) {
            if (cloudRecording) recorder.stop();
            else if (recognizer != null) recognizer.stopListening();
            return;
        }
        if (selectedLanguage != null && !selectedLanguage.voiceInputSupported()) {
            Toast.makeText(this, R.string.companion_voice_only_full, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!wantsCloudVoice() && !SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.companion_voice_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        startListening();
    }

    private void updateMicSendButton() {
        if (input == null || sendMicButton == null) return;
        boolean hasText = !input.getText().toString().trim().isEmpty();
        if (hasText) {
            sendMicButton.setIconResource(R.drawable.ic_next);
            sendMicButton.setContentDescription(getString(R.string.companion_send));
        } else {
            sendMicButton.setIconResource(listening ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
            sendMicButton.setContentDescription(getString(listening ? R.string.companion_stop : R.string.companion_speak));
        }
    }

    /** isiZulu and Sesotho go to the cloud recogniser when the backend has it switched on. */
    private boolean wantsCloudVoice() {
        return cloudVoiceAvailable && selectedLanguage != null && selectedLanguage.usesCloudVoice();
    }

    private void checkCloudVoice() {
        cloudVoiceAvailable = false;
        transcriber.checkAvailable(CompanionEndpoint.resolve(this, new Prefs(this)), available -> cloudVoiceAvailable = available);
    }

    private void startListening() {
        CompanionLanguage lang = selectedLanguage != null ? selectedLanguage : CompanionLanguage.EN;
        listeningLang = lang.code;
        if (wantsCloudVoice() && startCloudRecording(lang)) return;
        startBuiltInListening(lang);
    }

    /** Records up to 20 seconds, then sends the clip to the backend for transcription. */
    private boolean startCloudRecording(CompanionLanguage lang) {
        boolean started = recorder.start(this, 20_000, new SpeechRecorder.Listener() {
            @Override
            public void onRecorded(byte[] wav, long millis) {
                cloudRecording = false;
                if (millis < 600) {
                    stopListeningUi();
                    Toast.makeText(CompanionActivity.this, R.string.companion_voice_not_heard, Toast.LENGTH_SHORT).show();
                    return;
                }
                setSubtitle(R.string.companion_voice_processing);
                transcriber.transcribe(CompanionEndpoint.resolve(CompanionActivity.this, new Prefs(CompanionActivity.this)),
                        wav, lang.code, new CloudTranscriber.Callback() {
                            @Override
                            public void onText(String text) {
                                stopListeningUi();
                                send(text, "voice", lang.code);
                            }

                            @Override
                            public void onUnavailable() {
                                stopListeningUi();
                                cloudVoiceAvailable = false; // use the phone recogniser next time
                                Toast.makeText(CompanionActivity.this, R.string.companion_voice_cloud_failed, Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onNothingHeard() {
                                stopListeningUi();
                                Toast.makeText(CompanionActivity.this, R.string.companion_voice_not_heard, Toast.LENGTH_SHORT).show();
                            }
                        });
            }

            @Override
            public void onError() {
                cloudRecording = false;
                stopListeningUi();
                Toast.makeText(CompanionActivity.this, R.string.companion_voice_unavailable, Toast.LENGTH_SHORT).show();
            }
        });
        if (!started) return false;
        cloudRecording = true;
        listening = true;
        setSubtitle(R.string.companion_listening);
        updateMicSendButton();
        return true;
    }

    private void startBuiltInListening(CompanionLanguage lang) {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new SimpleRecognitionListener());
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.speechTag);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        setSubtitle(R.string.companion_listening);
        updateMicSendButton();
        recognizer.startListening(i);
    }

    private void stopListeningUi() {
        listening = false;
        setSubtitle(R.string.companion_online);
        updateMicSendButton();
    }

    private final class SimpleRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override
        public void onError(int error) {
            stopListeningUi();
            Toast.makeText(CompanionActivity.this, R.string.companion_voice_not_heard, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {
            stopListeningUi();
            ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (heard == null || heard.isEmpty() || heard.get(0).trim().isEmpty()) {
                Toast.makeText(CompanionActivity.this, R.string.companion_voice_not_heard, Toast.LENGTH_SHORT).show();
                return;
            }
            send(heard.get(0).trim(), "voice", listeningLang);
        }
    }

    private void setupTts() {
        tts = new TextToSpeech(this, status -> ttsReady = status == TextToSpeech.SUCCESS);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                speakingId = utteranceId;
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }
            @Override public void onDone(String utteranceId) {
                speakingId = null;
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }
            @Override public void onError(String utteranceId) {
                speakingId = null;
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }
        });
    }

    private Locale replyLocale(CompanionReply reply) {
        CompanionLanguage l = CompanionLanguage.fromCode(reply.languageCode);
        return l != null ? l.locale() : Locale.forLanguageTag("en-ZA");
    }

    private void speak(CompanionReply reply) {
        if (!ttsReady) return;
        Locale locale = replyLocale(reply);
        tts.setLanguage(locale);
        tts.speak(MarkdownLite.plain(reply.reply), TextToSpeech.QUEUE_FLUSH, null, "reply_" + System.currentTimeMillis());
    }

    private void stopSpeaking() {
        if (tts != null) tts.stop();
        speakingId = null;
        adapter.notifyDataSetChanged();
    }

    /** Replies may contain a little markdown; show bold as bold and never show the asterisks. */
    private static CharSequence styled(String text) {
        MarkdownLite.Result r = MarkdownLite.parse(text);
        SpannableStringBuilder out = new SpannableStringBuilder(r.text);
        for (int[] range : r.bold) {
            out.setSpan(new StyleSpan(Typeface.BOLD), range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    private void shareChat() {
        StringBuilder sb = new StringBuilder("WitsPath AI Companion Chat Transcript:\n\n");
        for (ChatItem item : items) {
            if (item.type == ChatItem.Type.USER) {
                sb.append("You: ").append(item.text).append("\n");
            } else if (item.type == ChatItem.Type.COMPANION) {
                sb.append("Companion: ").append(MarkdownLite.plain(item.text)).append("\n");
            }
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.companion_share_subject));
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent, getString(R.string.companion_share)));
    }

    private void shareRoute(CompanionReply.RouteCard c, String details) {
        String text = "Route to " + c.to + " (" + String.format(Locale.getDefault(), "%.1fm", c.distanceM) + "):\n" + details;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "WitsPath Route");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, getString(R.string.companion_share)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.shutdown();
        recorder.release();
        transcriber.shutdown();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private final class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int VIEW_TYPE_DAY = 0;
        private static final int VIEW_TYPE_WELCOME = 1;
        private static final int VIEW_TYPE_USER = 2;
        private static final int VIEW_TYPE_COMPANION = 3;
        private static final int VIEW_TYPE_TYPING = 4;
        private static final int VIEW_TYPE_ERROR = 5;

        private boolean isCompanionType(ChatItem.Type type) {
            return type == ChatItem.Type.WELCOME || type == ChatItem.Type.COMPANION || type == ChatItem.Type.TYPING || type == ChatItem.Type.ERROR;
        }

        @Override
        public int getItemViewType(int position) {
            switch (items.get(position).type) {
                case DAY_SEPARATOR: return VIEW_TYPE_DAY;
                case WELCOME: return VIEW_TYPE_WELCOME;
                case USER: return VIEW_TYPE_USER;
                case COMPANION: return VIEW_TYPE_COMPANION;
                case TYPING: return VIEW_TYPE_TYPING;
                case ERROR: return VIEW_TYPE_ERROR;
            }
            return VIEW_TYPE_COMPANION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            switch (viewType) {
                case VIEW_TYPE_DAY:
                    return new DayHolder(inflater.inflate(R.layout.item_chat_day, parent, false));
                case VIEW_TYPE_WELCOME:
                    return new WelcomeHolder(inflater.inflate(R.layout.item_chat_welcome, parent, false));
                case VIEW_TYPE_USER:
                    return new UserHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
                case VIEW_TYPE_ERROR:
                    return new ErrorHolder(inflater.inflate(R.layout.item_chat_error, parent, false));
                case VIEW_TYPE_TYPING:
                    return new TypingHolder(inflater.inflate(R.layout.item_chat_typing, parent, false));
                case VIEW_TYPE_COMPANION:
                default:
                    return new CompanionHolder(inflater.inflate(R.layout.item_chat_companion, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatItem item = items.get(position);
            boolean hasNextSame = (position + 1 < items.size());

            if (holder instanceof DayHolder) {
                ((DayHolder) holder).dayText.setText(item.text);
            } else if (holder instanceof WelcomeHolder) {
                WelcomeHolder wh = (WelcomeHolder) holder;
                wh.text.setText(item.text);
                wh.timestamp.setText(item.timestamp);
                boolean hasNextCompanion = hasNextSame && isCompanionType(items.get(position + 1).type);
                wh.avatar.setVisibility(hasNextCompanion ? View.INVISIBLE : View.VISIBLE);

                wh.suggestionsContainer.removeAllViews();
                String[] suggestions = getResources().getStringArray(R.array.companion_suggestions);
                for (String suggestion : suggestions) {
                    Chip chip = new Chip(CompanionActivity.this);
                    chip.setText(suggestion);
                    chip.setCheckable(false);
                    chip.setClickable(true);
                    chip.setOnClickListener(v -> send(suggestion, "text", null));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    lp.setMarginEnd(8);
                    chip.setLayoutParams(lp);
                    wh.suggestionsContainer.addView(chip);
                }
            } else if (holder instanceof UserHolder) {
                UserHolder uh = (UserHolder) holder;
                uh.text.setText(item.text);
                uh.timestamp.setText(item.timestamp);
                boolean hasNextUser = hasNextSame && items.get(position + 1).type == ChatItem.Type.USER;
                uh.avatar.setVisibility(hasNextUser ? View.INVISIBLE : View.VISIBLE);
            } else if (holder instanceof TypingHolder) {
                TypingHolder th = (TypingHolder) holder;
                boolean hasNextCompanion = hasNextSame && isCompanionType(items.get(position + 1).type);
                th.avatar.setVisibility(hasNextCompanion ? View.INVISIBLE : View.VISIBLE);
            } else if (holder instanceof ErrorHolder) {
                ErrorHolder eh = (ErrorHolder) holder;
                eh.text.setText(item.text);
                boolean hasNextCompanion = hasNextSame && isCompanionType(items.get(position + 1).type);
                eh.avatar.setVisibility(hasNextCompanion ? View.INVISIBLE : View.VISIBLE);
                eh.retryButton.setOnClickListener(v -> {
                    items.remove(position);
                    notifyItemRemoved(position);
                    if (!items.isEmpty()) {
                        ChatItem last = items.get(items.size() - 1);
                        if (last.type == ChatItem.Type.USER) {
                            send(last.text, "text", null);
                        }
                    }
                });
            } else if (holder instanceof CompanionHolder) {
                CompanionHolder ch = (CompanionHolder) holder;
                ch.text.setText(styled(item.text));
                ch.timestamp.setText(item.timestamp);
                boolean hasNextCompanion = hasNextSame && isCompanionType(items.get(position + 1).type);
                ch.avatar.setVisibility(hasNextCompanion ? View.INVISIBLE : View.VISIBLE);

                List<String> notes = new ArrayList<>();
                CompanionReply r = item.reply;
                if (r != null) {
                    if ("limited".equals(r.languageTier)) notes.add(getString(R.string.companion_badge_limited));
                    if ("unconfirmed".equals(r.languageSource)) notes.add(getString(R.string.companion_badge_unconfirmed));
                    if (r.route != null && r.route.fallbackToEnglish) {
                        CompanionLanguage l = CompanionLanguage.fromCode(r.languageCode);
                        notes.add(getString(R.string.companion_badge_directions_english, l != null ? l.displayName : r.languageCode));
                    }
                }
                ch.badge.setText(String.join(". ", notes));
                ch.badge.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);

                CompanionReply.RouteCard c = r == null ? null : r.route;
                if (c == null) {
                    ch.card.setVisibility(View.GONE);
                } else {
                    ch.card.setVisibility(View.VISIBLE);
                    ch.summary.setText(getString(R.string.companion_route_summary, c.to, String.format(Locale.getDefault(), "%.1f", c.distanceM)));
                    StringBuilder detail = new StringBuilder(getString(c.accessible ? R.string.companion_route_step_free : R.string.companion_route_not_step_free));
                    if (c.minutes == 1) {
                        detail.append('\n').append(getString(R.string.companion_route_time_one));
                    } else if (c.minutes > 1) {
                        detail.append('\n').append(getString(R.string.companion_route_time, c.minutes));
                    }
                    ch.detail.setText(detail);

                    View.OnClickListener showOnMap = v -> {
                        Intent intent = new Intent(CompanionActivity.this, HomeActivity.class);
                        intent.putExtra("from_node", c.fromNodeId);
                        intent.putExtra("to_node", c.toNodeId);
                        intent.putExtra("start_navigation", false);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                    };

                    View.OnClickListener startNav = v -> {
                        Intent intent = new Intent(CompanionActivity.this, HomeActivity.class);
                        intent.putExtra("from_node", c.fromNodeId);
                        intent.putExtra("to_node", c.toNodeId);
                        intent.putExtra("start_navigation", true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                    };

                    ch.showOnMap.setOnClickListener(showOnMap);
                    ch.startNav.setOnClickListener(startNav);
                    ch.share.setOnClickListener(v -> shareRoute(c, detail.toString()));
                }

                boolean isSpeaking = speakingId != null;
                ch.stopChip.setVisibility(isSpeaking ? View.VISIBLE : View.GONE);
                ch.stopChip.setOnClickListener(v -> stopSpeaking());
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class DayHolder extends RecyclerView.ViewHolder {
            final TextView dayText;
            DayHolder(View v) { super(v); dayText = v.findViewById(R.id.chatDayText); }
        }

        class WelcomeHolder extends RecyclerView.ViewHolder {
            final TextView text;
            final TextView timestamp;
            final ImageView avatar;
            final LinearLayout suggestionsContainer;
            WelcomeHolder(View v) {
                super(v);
                text = v.findViewById(R.id.chatWelcomeText);
                timestamp = v.findViewById(R.id.chatWelcomeTimestamp);
                avatar = v.findViewById(R.id.chatCompanionAvatar);
                suggestionsContainer = v.findViewById(R.id.chatSuggestionsContainer);
            }
        }

        class UserHolder extends RecyclerView.ViewHolder {
            final TextView text;
            final TextView timestamp;
            final ImageView avatar;
            UserHolder(View v) {
                super(v);
                text = v.findViewById(R.id.chatUserText);
                timestamp = v.findViewById(R.id.chatUserTimestamp);
                avatar = v.findViewById(R.id.chatUserAvatar);
            }
        }

        class CompanionHolder extends RecyclerView.ViewHolder {
            final TextView text;
            final TextView badge;
            final TextView timestamp;
            final ImageView avatar;
            final View card;
            final TextView summary;
            final TextView detail;
            final View showOnMap;
            final View startNav;
            final View share;
            final View stopChip;

            CompanionHolder(View v) {
                super(v);
                text = v.findViewById(R.id.chatCompanionText);
                badge = v.findViewById(R.id.chatBadgeText);
                timestamp = v.findViewById(R.id.chatCompanionTimestamp);
                avatar = v.findViewById(R.id.chatCompanionAvatar);
                card = v.findViewById(R.id.chatRouteCard);
                summary = v.findViewById(R.id.chatRouteSummary);
                detail = v.findViewById(R.id.chatRouteDetail);
                showOnMap = v.findViewById(R.id.chatShowOnMapButton);
                startNav = v.findViewById(R.id.chatStartNavButton);
                share = v.findViewById(R.id.chatRouteShareButton);
                stopChip = v.findViewById(R.id.chatStopChip);
            }
        }

        class TypingHolder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            TypingHolder(View v) {
                super(v);
                avatar = v.findViewById(R.id.chatCompanionAvatar);
            }
        }

        class ErrorHolder extends RecyclerView.ViewHolder {
            final TextView text;
            final ImageView avatar;
            final MaterialButton retryButton;
            ErrorHolder(View v) {
                super(v);
                text = v.findViewById(R.id.chatErrorText);
                avatar = v.findViewById(R.id.chatCompanionAvatar);
                retryButton = v.findViewById(R.id.chatRetryButton);
            }
        }
    }
}
