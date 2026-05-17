// scrybe-v2/SettingsScreen.jsx — Full settings with AI, integrations, storage

function V2Toggle({ on, onToggle }) {
  return (
    <div onClick={onToggle}
      style={{ width:44, height:24, borderRadius:12, flexShrink:0, cursor:'pointer',
               background: on ? V2_C.primary : V2_C.surfVar, position:'relative',
               transition:'background 0.2s' }}>
      <div style={{ width:18, height:18, borderRadius:'50%', position:'absolute',
                    top:3, left: on ? 23 : 3, flexShrink:0,
                    background: on ? V2_C.primCont : V2_C.onSurfVar,
                    transition:'left 0.2s' }} />
    </div>
  );
}

function SettingsSection({ title, children }) {
  return (
    <div style={{ display:'flex', flexDirection:'column', gap:2 }}>
      <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.07em',
                    padding:'10px 2px 6px' }}>{title}</div>
      <div style={{ background:V2_C.surfHigh, borderRadius:16, overflow:'hidden',
                    border:`1px solid ${V2_C.border}` }}>
        {children}
      </div>
    </div>
  );
}

function SettingsRow({ icon, label, sub, value, color, onPress, isLast, toggle, toggleOn, onToggle }) {
  return (
    <div onClick={onPress}
      style={{ padding:'11px 14px', display:'flex', alignItems:'center', gap:12,
               cursor: onPress||onToggle ? 'pointer' : 'default',
               borderBottom: isLast ? 'none' : `1px solid ${V2_C.border}` }}>
      {icon && (
        <div style={{ width:32, height:32, borderRadius:9, background:V2_C.surfVar,
                      display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          <V2Ico name={icon} size={17} color={color||V2_C.onSurfVar} />
        </div>
      )}
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontSize:14, color:V2_C.onSurface }}>{label}</div>
        {sub && <div style={{ fontSize:11, color:V2_C.muted, marginTop:1, lineHeight:'15px' }}>{sub}</div>}
      </div>
      {toggle
        ? <V2Toggle on={toggleOn} onToggle={onToggle} />
        : value
          ? <span style={{ fontSize:13, color:V2_C.muted }}>{value}</span>
          : onPress
            ? <V2Ico name="chevron_right" size={16} color={V2_C.muted} />
            : null
      }
    </div>
  );
}

const TRANSCRIPTION_MODELS = [
  { id:'whisper-cloud', icon:'cloud',      name:'OpenAI Whisper',  desc:'Cloud · best accuracy, 100+ languages', status:'Active',    selected:true,  badge:'cloud'  },
  { id:'whisper-local', icon:'smartphone', name:'Whisper (local)', desc:'On-device · 75MB · no internet needed', status:'Download',  selected:false, badge:'local'  },
  { id:'assembly',      icon:'graphic_eq', name:'AssemblyAI',      desc:'Cloud · real-time streaming + diarization', status:'Connect', selected:false, badge:'cloud' },
];

const LLM_MODELS = [
  { id:'gpt4o',    icon:'auto_awesome',  name:'GPT-4o',           desc:'Cloud · OpenAI · best reasoning',        status:'Active',   selected:true,  badge:'cloud'  },
  { id:'claude',   icon:'psychology',    name:'Claude Haiku',     desc:'Cloud · Anthropic · fast + precise',     status:'Connect',  selected:false, badge:'cloud'  },
  { id:'ollama',   icon:'terminal',      name:'Ollama (local)',   desc:'On-device · Llama 3 / Mistral · private',status:'Setup',    selected:false, badge:'local'  },
  { id:'lmstudio', icon:'developer_mode',name:'LM Studio',        desc:'On-device · any GGUF model',             status:'Setup',    selected:false, badge:'local'  },
];

function ModelCard({ model, onSelect }) {
  const isCloud = model.badge === 'cloud';
  return (
    <div onClick={onSelect}
      style={{ background: model.selected ? '#091D31' : V2_C.surfVar,
               borderRadius:12, padding:'11px 12px',
               display:'flex', alignItems:'center', gap:10,
               border:`1px solid ${model.selected ? V2_C.primary+'44' : 'transparent'}`,
               cursor:'pointer' }}>
      <div style={{ width:34, height:34, borderRadius:10,
                    background: model.selected ? `${V2_C.primary}20` : V2_C.surfHigh,
                    display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <V2Ico name={model.icon} size={18} color={model.selected?V2_C.primary:V2_C.muted} />
      </div>
      <div style={{ flex:1, minWidth:0 }}>
        <V2Row style={{ gap:6 }}>
          <span style={{ fontSize:13, fontWeight:model.selected?600:400,
                         color:model.selected?V2_C.primary:V2_C.onSurface }}>{model.name}</span>
          <div style={{ background: isCloud?'#091D31':'#0A1812', borderRadius:9999,
                        padding:'1px 6px', flexShrink:0 }}>
            <span style={{ fontSize:9, fontWeight:600,
                           color:isCloud?V2_C.primary:V2_C.secondary,
                           letterSpacing:'0.05em' }}>
              {isCloud?'CLOUD':'LOCAL'}
            </span>
          </div>
        </V2Row>
        <div style={{ fontSize:11, color:V2_C.muted, marginTop:1,
                      overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
          {model.desc}
        </div>
      </div>
      <div style={{ background: model.selected?'#003A63':V2_C.surfHigh,
                    borderRadius:9999, padding:'3px 8px', flexShrink:0 }}>
        <span style={{ fontSize:10, fontWeight:500,
                       color:model.selected?V2_C.primary:V2_C.muted }}>
          {model.status}
        </span>
      </div>
    </div>
  );
}

function AIProviderSection() {
  const [sttModel, setSttModel] = React.useState('whisper-cloud');
  const [llmModel, setLlmModel] = React.useState('gpt4o');
  const [apiKeyOk, setApiKeyOk] = React.useState(true);

  return (
    <SettingsSection title="AI PROVIDER">
      <div style={{ padding:'12px 14px 0', display:'flex', flexDirection:'column', gap:12 }}>

        {/* Transcription */}
        <div>
          <V2Row style={{ gap:6, marginBottom:8 }}>
            <V2Ico name="mic" size={13} color={V2_C.muted} />
            <span style={{ fontSize:11, color:V2_C.muted, fontWeight:600,
                           letterSpacing:'0.05em' }}>TRANSCRIPTION</span>
          </V2Row>
          <div style={{ display:'flex', flexDirection:'column', gap:6 }}>
            {TRANSCRIPTION_MODELS.map(m=>(
              <ModelCard key={m.id} model={{ ...m, selected:sttModel===m.id }}
                onSelect={()=>setSttModel(m.id)} />
            ))}
          </div>
        </div>

        <div style={{ height:1, background:V2_C.border }} />

        {/* LLM */}
        <div>
          <V2Row style={{ gap:6, marginBottom:8 }}>
            <V2Ico name="auto_awesome" size={13} color={V2_C.muted} />
            <span style={{ fontSize:11, color:V2_C.muted, fontWeight:600,
                           letterSpacing:'0.05em' }}>AI TRANSFORMS & LANGUAGE MODEL</span>
          </V2Row>
          <div style={{ display:'flex', flexDirection:'column', gap:6 }}>
            {LLM_MODELS.map(m=>(
              <ModelCard key={m.id} model={{ ...m, selected:llmModel===m.id }}
                onSelect={()=>setLlmModel(m.id)} />
            ))}
          </div>
        </div>

        {/* API key status */}
        <div style={{ background:apiKeyOk?'#0A2018':'#1C0A00', borderRadius:10,
                      padding:'9px 12px', display:'flex', alignItems:'center', gap:8,
                      marginBottom:4 }}>
          <V2Ico name={apiKeyOk?'vpn_key':'warning'} size={15}
            color={apiKeyOk?V2_C.secondary:V2_C.tertiary} />
          <span style={{ fontSize:12, color:apiKeyOk?V2_C.secondary:V2_C.tertiary, flex:1 }}>
            {apiKeyOk?'API key connected · OpenAI':'API key required for cloud models'}
          </span>
          <span style={{ fontSize:12, color:V2_C.muted, cursor:'pointer' }}>
            {apiKeyOk?'Change':'Add'}
          </span>
        </div>
      </div>
    </SettingsSection>
  );
}

function IntegrationRow({ icon, label, sub, color, connected, isLast }) {
  return (
    <div style={{ padding:'11px 14px', display:'flex', alignItems:'center', gap:12,
                  borderBottom: isLast?'none':`1px solid ${V2_C.border}`, cursor:'pointer' }}>
      <div style={{ width:34, height:34, borderRadius:10, background:V2_C.surfVar,
                    display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <V2Ico name={icon} size={18} color={color} />
      </div>
      <div style={{ flex:1 }}>
        <div style={{ fontSize:14, color:V2_C.onSurface }}>{label}</div>
        <div style={{ fontSize:11, color:V2_C.muted, marginTop:1 }}>{sub}</div>
      </div>
      <div style={{ background: connected ? '#0A2018' : V2_C.surfVar,
                    borderRadius:9999, padding:'3px 9px' }}>
        <span style={{ fontSize:11, color:connected?V2_C.secondary:V2_C.muted,
                       fontWeight:connected?500:400 }}>
          {connected ? 'Connected' : 'Connect'}
        </span>
      </div>
    </div>
  );
}

function StorageBar({ used, total }) {
  const pct = used/total;

  const statBlocks = [
    { icon:'mic',          label:'Recordings', value:'48',       color:V2_C.primary   },
    { icon:'timer',        label:'Total time',  value:'14h 22m',  color:V2_C.secondary },
    { icon:'record_voice_over', label:'Speakers', value:'12',    color:V2_C.purple    },
    { icon:'checklist',    label:'Tasks made', value:'134',      color:V2_C.tertiary  },
  ];

  const aiUsage = [
    { icon:'graphic_eq',  label:'Transcription', value:'6.2h used', pct:0.62, color:V2_C.primary,   sub:'of 10h / month' },
    { icon:'auto_awesome',label:'AI transforms',  value:'312 calls',  pct:0.31, color:V2_C.amber,    sub:'of ~1000 / month' },
  ];

  return (
    <div style={{ display:'flex', flexDirection:'column', gap:0 }}>

      {/* Recording stats grid */}
      <div style={{ padding:'12px 14px', borderBottom:`1px solid ${V2_C.border}` }}>
        <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em', marginBottom:10 }}>
          RECORDING STATS
        </div>
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:8 }}>
          {statBlocks.map(s=>(
            <div key={s.label}
              style={{ background:V2_C.surfVar, borderRadius:12, padding:'10px 12px' }}>
              <V2Row style={{ gap:6, marginBottom:4 }}>
                <V2Ico name={s.icon} size={13} color={s.color} />
                <span style={{ fontSize:11, color:V2_C.muted }}>{s.label}</span>
              </V2Row>
              <div style={{ fontSize:20, fontWeight:600, color:V2_C.onSurface,
                            letterSpacing:'-0.3px' }}>{s.value}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Audio storage bar */}
      <div style={{ padding:'12px 14px', borderBottom:`1px solid ${V2_C.border}`,
                    display:'flex', flexDirection:'column', gap:8 }}>
        <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em' }}>
          AUDIO STORAGE
        </div>
        <V2Row style={{ justifyContent:'space-between' }}>
          <span style={{ fontSize:13, color:V2_C.onSurface }}>Used</span>
          <span style={{ fontSize:12, color:V2_C.muted }}>{used} GB of {total} GB</span>
        </V2Row>
        <div style={{ height:5, background:V2_C.surfVar, borderRadius:3 }}>
          <div style={{ width:`${pct*100}%`, height:'100%', borderRadius:3,
                        background: pct>0.8?V2_C.tertiary:V2_C.primary }} />
        </div>
        <V2Row style={{ justifyContent:'space-between' }}>
          <span style={{ fontSize:11, color:V2_C.muted }}>Auto-delete after</span>
          <span style={{ fontSize:11, color:V2_C.primary, cursor:'pointer' }}>Never →</span>
        </V2Row>
      </div>

      {/* AI usage */}
      <div style={{ padding:'12px 14px', display:'flex', flexDirection:'column', gap:10 }}>
        <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em' }}>
          AI USAGE · THIS MONTH
        </div>
        {aiUsage.map(u=>(
          <div key={u.label}>
            <V2Row style={{ justifyContent:'space-between', marginBottom:5 }}>
              <V2Row style={{ gap:6 }}>
                <V2Ico name={u.icon} size={13} color={u.color} />
                <span style={{ fontSize:13, color:V2_C.onSurface }}>{u.label}</span>
              </V2Row>
              <V2Row style={{ gap:6 }}>
                <span style={{ fontSize:12, color:u.color, fontWeight:500 }}>{u.value}</span>
                <span style={{ fontSize:11, color:V2_C.muted }}>{u.sub}</span>
              </V2Row>
            </V2Row>
            <div style={{ height:4, background:V2_C.surfVar, borderRadius:2 }}>
              <div style={{ width:`${u.pct*100}%`, height:'100%', borderRadius:2,
                            background:u.color, opacity:0.75 }} />
            </div>
          </div>
        ))}
        <div style={{ background:V2_C.surfVar, borderRadius:10, padding:'8px 12px',
                      display:'flex', alignItems:'center', justifyContent:'space-between' }}>
          <V2Row style={{ gap:6 }}>
            <V2Ico name="receipt_long" size={14} color={V2_C.muted} />
            <span style={{ fontSize:12, color:V2_C.muted }}>Est. this month</span>
          </V2Row>
          <span style={{ fontSize:13, fontWeight:600, color:V2_C.onSurface }}>~$1.84</span>
        </div>
      </div>
    </div>
  );
}

function SettingsScreen({ onBack, onOpenProfiles }) {
  const [autoTranscribe, setAutoTranscribe] = React.useState(true);
  const [keepScreen, setKeepScreen] = React.useState(false);
  const [liveTrans, setLiveTrans] = React.useState(true);
  const [speakerID, setSpeakerID] = React.useState(true);
  const [locationTag, setLocationTag] = React.useState(true);

  return (
    <div style={{ background:V2_C.bg, height:'100%', display:'flex',
                  flexDirection:'column', fontFamily:'DM Sans, sans-serif' }}>
      <V2TopBar back onBack={onBack} title="Settings" />

      <div style={{ flex:1, overflowY:'auto', padding:'0 14px 24px',
                    display:'flex', flexDirection:'column', gap:0 }}>

        {/* Profiles — first class */}
        <SettingsSection title="INTELLIGENCE">
          <div onClick={onOpenProfiles}
            style={{ padding:'12px 14px', display:'flex', alignItems:'center', gap:12,
                     borderBottom:`1px solid ${V2_C.border}`, cursor:'pointer' }}>
            <div style={{ width:32, height:32, borderRadius:9, background:'#091D31',
                          display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
              <V2Ico name="tune" size={17} color={V2_C.primary} />
            </div>
            <div style={{ flex:1 }}>
              <div style={{ fontSize:14, color:V2_C.onSurface }}>Profiles</div>
              <div style={{ fontSize:11, color:V2_C.muted }}>5 active · 3 with auto-trigger</div>
            </div>
            <V2Ico name="chevron_right" size={16} color={V2_C.muted} />
          </div>
          <SettingsRow icon="auto_awesome" color={V2_C.primary}
            label="Live transcription" sub="Real-time text while recording"
            toggle toggleOn={liveTrans} onToggle={()=>setLiveTrans(p=>!p)} />
          <SettingsRow icon="record_voice_over" color={V2_C.purple}
            label="Speaker identification" sub="Color-code multiple voices"
            toggle toggleOn={speakerID} onToggle={()=>setSpeakerID(p=>!p)} />
          <SettingsRow icon="task_alt" color={V2_C.secondary}
            label="Auto-extract tasks" sub="Always pull action items from transcript"
            toggle toggleOn={true} onToggle={()=>{}} isLast />
        </SettingsSection>

        {/* AI Provider */}
        <AIProviderSection />

        {/* Recording */}
        <SettingsSection title="RECORDING">
          <SettingsRow icon="mic" color={V2_C.primary}
            label="Auto-transcribe on save" sub="Begins immediately after stopping"
            toggle toggleOn={autoTranscribe} onToggle={()=>setAutoTranscribe(p=>!p)} />
          <SettingsRow icon="brightness_high" color={V2_C.amber}
            label="Keep screen on" sub="Prevents sleep while recording"
            toggle toggleOn={keepScreen} onToggle={()=>setKeepScreen(p=>!p)} />
          <SettingsRow icon="place" color={V2_C.tertiary}
            label="Tag location" sub="Auto-add location to each session"
            toggle toggleOn={locationTag} onToggle={()=>setLocationTag(p=>!p)} />
          <SettingsRow icon="high_quality" color={V2_C.onSurfVar}
            label="Audio quality" value="High (AAC 128k)" onPress={()=>{}} isLast />
        </SettingsSection>

        {/* Integrations */}
        <SettingsSection title="INTEGRATIONS">
          <IntegrationRow icon="event"         label="Calendar"   sub="Add tasks with dates"         color="#4285F4" connected={true}  />
          <IntegrationRow icon="notifications" label="Reminders"  sub="Create iOS reminders"         color="#FF5252" connected={true}  />
          <IntegrationRow icon="article"       label="Notion"     sub="Export sessions as pages"     color={V2_C.onSurface} connected={false} />
          <IntegrationRow icon="chat"          label="Slack"      sub="Post summaries to channels"   color="#E01E5A" connected={false} />
          <IntegrationRow icon="bolt"          label="Shortcuts"  sub="iOS Shortcuts automations"    color={V2_C.amber} connected={true} />
          <IntegrationRow icon="add_circle_outline" label="Add integration" sub="Browse available integrations" color={V2_C.muted} connected={false} isLast />
        </SettingsSection>

        {/* Storage */}
        <SettingsSection title="STORAGE & USAGE">
          <StorageBar used={2.3} total={8} />
        </SettingsSection>

        {/* Appearance */}
        <SettingsSection title="APPEARANCE">
          <div style={{ padding:'12px 14px' }}>
            <div style={{ display:'flex', background:V2_C.surfVar, borderRadius:10,
                          padding:3, gap:2 }}>
              {['System','Light','Dark'].map((t,i)=>(
                <div key={t}
                  style={{ flex:1, textAlign:'center', padding:'7px 0',
                           background: i===2 ? V2_C.surfHigh : 'transparent',
                           borderRadius:8, fontSize:13, cursor:'pointer',
                           fontWeight: i===2 ? 600 : 400,
                           color: i===2 ? V2_C.primary : V2_C.muted }}>
                  {t}
                </div>
              ))}
            </div>
          </div>
        </SettingsSection>

        {/* About */}
        <SettingsSection title="ABOUT">
          <SettingsRow label="Version" value="2.0.0 (build 214)" isLast={false} />
          <SettingsRow label="Privacy policy" onPress={()=>{}} isLast={false} />
          <SettingsRow label="Send feedback" onPress={()=>{}} isLast />
        </SettingsSection>

      </div>
    </div>
  );
}

Object.assign(window, { SettingsScreen, V2Toggle });
