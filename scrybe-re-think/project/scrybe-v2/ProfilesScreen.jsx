// scrybe-v2/ProfilesScreen.jsx
// Profile list + Pipeline builder detail

// ── Profile data ─────────────────────────────────────────────
const PROFILES = [
  {
    id: 'standup',
    name: 'Daily Standup',
    icon: 'groups',
    color: V2_C.primary,
    bg: '#091D31',
    mode: 'meeting',
    steps: [
      { type:'transcribe', label:'Transcribe', icon:'mic',          color: V2_C.onSurfVar },
      { type:'tasks',      label:'Extract tasks', icon:'checklist', color: V2_C.secondary },
      { type:'send',       label:'Post to Slack', icon:'chat',      color: '#E01E5A'      },
    ],
    triggers: ['Auto on weekdays 9–10 AM'],
    sessions: 14,
    builtin: false,
  },
  {
    id: 'ideas',
    name: 'Product Ideas',
    icon: 'lightbulb',
    color: V2_C.amber,
    bg: '#1A1400',
    mode: 'idea',
    steps: [
      { type:'transcribe', label:'Transcribe',      icon:'mic',       color: V2_C.onSurfVar },
      { type:'ai',         label:'Brainstorm list', icon:'format_list_bulleted', color: V2_C.amber },
      { type:'send',       label:'Save to Notion',  icon:'article',   color: '#E2E8F0'      },
    ],
    triggers: [],
    sessions: 9,
    builtin: false,
  },
  {
    id: 'interview',
    name: 'Interview',
    icon: 'person_search',
    color: V2_C.tertiary,
    bg: '#1C0E00',
    mode: 'interview',
    steps: [
      { type:'transcribe', label:'Transcribe',    icon:'mic',          color: V2_C.onSurfVar },
      { type:'ai',         label:'Q&A format',    icon:'quiz',         color: V2_C.tertiary  },
      { type:'ai',         label:'Highlights',    icon:'star',         color: V2_C.amber     },
      { type:'send',       label:'Email to self', icon:'mail',         color: V2_C.tertiary  },
    ],
    triggers: [],
    sessions: 3,
    builtin: false,
  },
  {
    id: 'journal',
    name: 'Voice Journal',
    icon: 'book',
    color: V2_C.purple,
    bg: '#130E22',
    mode: 'journal',
    steps: [
      { type:'transcribe', label:'Transcribe',   icon:'mic',          color: V2_C.onSurfVar },
      { type:'ai',         label:'Clean up',     icon:'auto_fix_high',color: V2_C.purple    },
    ],
    triggers: ['Auto on weekdays 9 PM'],
    sessions: 22,
    builtin: false,
  },
  {
    id: 'podcast',
    name: 'Podcast Episode',
    icon: 'podcast',
    color: V2_C.pink,
    bg: '#1E0A12',
    mode: 'story',
    steps: [
      { type:'transcribe', label:'Transcribe',      icon:'mic',              color: V2_C.onSurfVar },
      { type:'ai',         label:'Chapter markers', icon:'bookmark',         color: V2_C.pink      },
      { type:'ai',         label:'Narrative edit',  icon:'auto_stories',     color: V2_C.pink      },
      { type:'send',       label:'Export transcript',icon:'download',        color: V2_C.onSurfVar },
    ],
    triggers: [],
    sessions: 7,
    builtin: true,
  },
];

// ── Pipeline step node ────────────────────────────────────────
function PipelineStep({ step, isLast }) {
  return (
    <div style={{ display:'flex', alignItems:'center', gap:0 }}>
      <div style={{ background:V2_C.surfVar, borderRadius:10,
                    padding:'7px 10px', display:'flex', alignItems:'center', gap:6 }}>
        <V2Ico name={step.icon} size={14} color={step.color} />
        <span style={{ fontSize:12, color:step.color, fontWeight:500, whiteSpace:'nowrap' }}>
          {step.label}
        </span>
      </div>
      {!isLast && (
        <V2Ico name="arrow_forward" size={14} color={V2_C.muted}
          style={{ flexShrink:0, margin:'0 3px' }} />
      )}
    </div>
  );
}

// ── Profile card ──────────────────────────────────────────────
function ProfileCard({ profile, onOpen }) {
  return (
    <div onClick={()=>onOpen(profile.id)}
      style={{ background:V2_C.surfHigh, borderRadius:18, padding:14,
               border:`1px solid ${V2_C.border}`, cursor:'pointer',
               display:'flex', flexDirection:'column', gap:10 }}>
      {/* Header */}
      <V2Row style={{ justifyContent:'space-between' }}>
        <V2Row style={{ gap:10 }}>
          <div style={{ width:38, height:38, borderRadius:12, background:profile.bg,
                        border:`1px solid ${profile.color}33`,
                        display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <V2Ico name={profile.icon} size={20} color={profile.color} />
          </div>
          <div>
            <div style={{ fontSize:14, fontWeight:600, color:V2_C.onSurface }}>{profile.name}</div>
            <V2Row style={{ gap:6, marginTop:2 }}>
              <V2ModeBadge modeId={profile.mode} />
              {profile.builtin && (
                <div style={{ background:V2_C.surfVar, borderRadius:9999, padding:'2px 7px' }}>
                  <span style={{ fontSize:10, color:V2_C.muted }}>Built-in</span>
                </div>
              )}
            </V2Row>
          </div>
        </V2Row>
        <div style={{ display:'flex', flexDirection:'column', alignItems:'flex-end', gap:2 }}>
          <span style={{ fontSize:11, color:V2_C.muted }}>{profile.sessions} uses</span>
          {profile.triggers.length>0 && (
            <V2Row style={{ gap:3 }}>
              <V2Ico name="bolt" size={11} color={V2_C.amber} />
              <span style={{ fontSize:10, color:V2_C.amber }}>Auto</span>
            </V2Row>
          )}
        </div>
      </V2Row>

      {/* Pipeline steps */}
      <div style={{ display:'flex', alignItems:'center', flexWrap:'wrap', gap:4 }}>
        {profile.steps.map((step, i)=>(
          <PipelineStep key={i} step={step} isLast={i===profile.steps.length-1} />
        ))}
      </div>
    </div>
  );
}

// ── Profile detail / builder ──────────────────────────────────
const TRANSFORM_OPTIONS = [
  { icon:'auto_fix_high',          label:'Clean up',           color:V2_C.primary   },
  { icon:'summarize',              label:'Summarize',          color:V2_C.primary   },
  { icon:'checklist',              label:'Extract tasks',      color:V2_C.secondary },
  { icon:'format_list_bulleted',   label:'Brainstorm list',    color:V2_C.amber     },
  { icon:'quiz',                   label:'Q&A format',         color:V2_C.tertiary  },
  { icon:'bookmark',               label:'Chapter markers',    color:V2_C.pink      },
  { icon:'psychology',             label:'Sentiment analysis', color:V2_C.purple    },
  { icon:'translate',              label:'Translate',          color:V2_C.amber     },
  { icon:'auto_stories',           label:'Narrative edit',     color:V2_C.pink      },
];

const DESTINATION_OPTIONS = [
  { icon:'event',         label:'Calendar',      color:'#4285F4' },
  { icon:'notifications', label:'Reminders',     color:'#FF5252' },
  { icon:'article',       label:'Notion',        color:V2_C.onSurface },
  { icon:'chat',          label:'Slack',         color:'#E01E5A' },
  { icon:'mail',          label:'Email',         color:V2_C.tertiary },
  { icon:'bolt',          label:'Shortcuts',     color:V2_C.amber },
  { icon:'download',      label:'Export file',   color:V2_C.onSurfVar },
  { icon:'ios_share',     label:'Share sheet',   color:V2_C.onSurfVar },
];

function BuilderSection({ title, icon, color, badge, defaultOpen=false, children }) {
  const [open, setOpen] = React.useState(defaultOpen);
  return (
    <div style={{ background:V2_C.surfHigh, borderRadius:16,
                  border:`1px solid ${V2_C.border}` }}>
      <div onClick={()=>setOpen(o=>!o)}
        style={{ padding:'13px 14px', display:'flex', alignItems:'center',
                 gap:10, cursor:'pointer' }}>
        <div style={{ width:32, height:32, borderRadius:9,
                      background:`${color}18`,
                      display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          <V2Ico name={icon} size={16} color={color} />
        </div>
        <span style={{ fontSize:14, fontWeight:600, color:V2_C.onSurface, flex:1 }}>{title}</span>
        {badge > 0 && (
          <div style={{ background:`${color}22`, borderRadius:9999, padding:'2px 9px' }}>
            <span style={{ fontSize:11, color, fontWeight:600 }}>{badge} on</span>
          </div>
        )}
        <V2Ico name={open?'expand_less':'expand_more'} size={18} color={V2_C.muted} />
      </div>
      {open && (
        <div style={{ borderTop:`1px solid ${V2_C.border}`,
                      display:'flex', flexDirection:'column' }}>
          {children}
        </div>
      )}
    </div>
  );
}

function OptionRow({ icon, label, sub, color, selected, onToggle, isLast }) {
  return (
    <div onClick={onToggle}
      style={{ display:'flex', alignItems:'center', gap:12, padding:'11px 14px',
               borderBottom: isLast ? 'none' : `1px solid ${V2_C.border}`,
               cursor:'pointer', background: selected?`${color}09`:'transparent' }}>
      <div style={{ width:34, height:34, borderRadius:10, flexShrink:0,
                    background: selected ? `${color}20` : V2_C.surfVar,
                    display:'flex', alignItems:'center', justifyContent:'center' }}>
        <V2Ico name={icon} size={18} color={selected?color:V2_C.muted} />
      </div>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontSize:14, color:selected?V2_C.onSurface:V2_C.onSurfVar,
                      fontWeight:selected?500:400 }}>{label}</div>
        {sub && <div style={{ fontSize:11, color:V2_C.muted, marginTop:1 }}>{sub}</div>}
      </div>
      <V2Toggle on={selected} onToggle={onToggle} />
    </div>
  );
}

function ProfileDetail({ profileId, onBack, onEdit }) {
  const profile = PROFILES.find(p=>p.id===profileId)||PROFILES[0];
  const [selectedTransforms, setSelectedTransforms] = React.useState(
    profile.steps.filter(s=>s.type==='ai').map(s=>s.label)
  );
  const [selectedDests, setSelectedDests] = React.useState(
    profile.steps.filter(s=>s.type==='send').map(s=>s.label)
  );

  const toggleT = (lbl) => setSelectedTransforms(prev=>
    prev.includes(lbl) ? prev.filter(x=>x!==lbl) : [...prev, lbl]);
  const toggleD = (lbl) => setSelectedDests(prev=>
    prev.includes(lbl) ? prev.filter(x=>x!==lbl) : [...prev, lbl]);

  return (
    <div style={{ background:V2_C.bg, height:'100%', display:'flex',
                  flexDirection:'column', fontFamily:'DM Sans, sans-serif' }}>
      <V2TopBar
        back onBack={onBack}
        title={profile.name}
        right={
          <div style={{ padding:8, cursor:'pointer', display:'flex' }}>
            <V2Ico name="more_vert" />
          </div>
        }
      />

      <div style={{ flex:1, overflowY:'auto', padding:'0 14px 20px',
                    display:'flex', flexDirection:'column', gap:10 }}>

        {/* Profile header */}
        <div style={{ background:profile.bg, borderRadius:18, padding:16,
                      border:`1px solid ${profile.color}25`,
                      display:'flex', alignItems:'center', gap:12 }}>
          <div style={{ width:48, height:48, borderRadius:14, background:`${profile.color}18`,
                        display:'flex', alignItems:'center', justifyContent:'center' }}>
            <V2Ico name={profile.icon} size={24} color={profile.color} />
          </div>
          <div style={{ flex:1 }}>
            <V2Row style={{ gap:8, flexWrap:'wrap' }}>
              <V2ModeBadge modeId={profile.mode} size="md" />
              <span style={{ fontSize:12, color:V2_C.muted }}>{profile.sessions} sessions</span>
            </V2Row>
            <div style={{ fontSize:12, color:V2_C.muted, marginTop:4 }}>
              {profile.triggers.length>0
                ? `Auto-trigger: ${profile.triggers[0]}`
                : 'Manual trigger only'}
            </div>
          </div>
        </div>

        {/* Pipeline visual — compact flow */}
        <div style={{ background:V2_C.surface, borderRadius:14, padding:'10px 14px',
                      border:`1px solid ${V2_C.border}`,
                      display:'flex', alignItems:'center', gap:4, flexWrap:'wrap' }}>
          {profile.steps.map((step,i)=>(
            <React.Fragment key={i}>
              <div style={{ display:'flex', alignItems:'center', gap:5,
                            background:`${step.color}14`, borderRadius:8, padding:'4px 8px' }}>
                <V2Ico name={step.icon} size={13} color={step.color} />
                <span style={{ fontSize:12, color:step.color, fontWeight:500 }}>{step.label}</span>
              </div>
              {i<profile.steps.length-1 && (
                <V2Ico name="arrow_forward" size={13} color={V2_C.muted} />
              )}
            </React.Fragment>
          ))}
        </div>

        {/* Step 1: Mode */}
        <BuilderSection title="Mode" icon="tune" color={V2_C.primary}
          badge={1} defaultOpen={true}>
          {MODES.filter(m=>m.id!=='new').map((m,i,arr)=>(
            <OptionRow key={m.id} icon={m.icon} label={m.label}
              sub={m.output} color={m.color}
              selected={m.id===profile.mode}
              onToggle={()=>{}}
              isLast={i===arr.length-1} />
          ))}
        </BuilderSection>

        {/* Step 2: Transforms */}
        <BuilderSection title="AI Transforms" icon="auto_awesome" color={V2_C.primary}
          badge={selectedTransforms.length} defaultOpen={false}>
          {TRANSFORM_OPTIONS.map((t,i,arr)=>(
            <OptionRow key={t.label} icon={t.icon} label={t.label}
              color={t.color}
              selected={selectedTransforms.includes(t.label)}
              onToggle={()=>toggleT(t.label)}
              isLast={i===arr.length-1} />
          ))}
        </BuilderSection>

        {/* Step 3: Destinations */}
        <BuilderSection title="Send to" icon="send" color={V2_C.secondary}
          badge={selectedDests.length} defaultOpen={false}>
          {DESTINATION_OPTIONS.map((d,i,arr)=>(
            <OptionRow key={d.label} icon={d.icon} label={d.label}
              color={d.color}
              selected={selectedDests.includes(d.label)}
              onToggle={()=>toggleD(d.label)}
              isLast={i===arr.length-1} />
          ))}
        </BuilderSection>

        {/* Step 4: Auto-trigger */}
        <BuilderSection title="Auto-trigger" icon="bolt" color={V2_C.amber}
          badge={profile.triggers.length} defaultOpen={false}>
          {[
            { icon:'schedule',       label:'Time-based',    sub:'e.g. weekdays 9 AM',  on: profile.triggers.length>0 },
            { icon:'place',          label:'Location',      sub:'e.g. at the office',  on: false },
            { icon:'wifi',           label:'Network',       sub:'e.g. on work Wi-Fi',  on: false },
            { icon:'calendar_today', label:'Calendar event',sub:'When in a meeting',   on: false },
          ].map((r,i,arr)=>(
            <OptionRow key={r.label} icon={r.icon} label={r.label}
              sub={r.sub} color={V2_C.amber}
              selected={r.on} onToggle={()=>{}}
              isLast={i===arr.length-1} />
          ))}
        </BuilderSection>

        {/* Save */}
        <button style={{ background:V2_C.primary, border:'none', borderRadius:14,
                         padding:'13px 0', fontSize:15, fontWeight:600, color:V2_C.primCont,
                         cursor:'pointer', fontFamily:'DM Sans, sans-serif' }}>
          Save profile
        </button>
      </div>
    </div>
  );
}

function ProfilesScreen({ onBack }) {
  const [detailId, setDetailId] = React.useState(null);
  const [creating, setCreating] = React.useState(false);

  if(creating) {
    return <CreateProfileScreen
      onBack={()=>setCreating(false)}
      onSave={()=>setCreating(false)}
    />;
  }

  if(detailId) {
    return <ProfileDetail profileId={detailId} onBack={()=>setDetailId(null)} />;
  }

  return (
    <div style={{ background:V2_C.bg, height:'100%', display:'flex',
                  flexDirection:'column', fontFamily:'DM Sans, sans-serif' }}>
      <V2TopBar
        back onBack={onBack}
        title="Profiles"
        right={
          <div onClick={()=>setCreating(true)}
            style={{ padding:8, cursor:'pointer', display:'flex' }}>
            <V2Ico name="add" color={V2_C.primary} />
          </div>
        }
      />

      <div style={{ flex:1, overflowY:'auto', padding:'0 14px 20px',
                    display:'flex', flexDirection:'column', gap:8 }}>

        {/* Explainer */}
        <div style={{ background:'#091D31', borderRadius:14, padding:'10px 13px',
                      border:`1px solid ${V2_C.primary}20`,
                      display:'flex', gap:10 }}>
          <V2Ico name="info" size={16} color={V2_C.primary} style={{ marginTop:1 }} />
          <div style={{ fontSize:12, color:V2_C.onSurfVar, lineHeight:'17px' }}>
            Profiles are reusable pipelines — they chain a mode, AI transforms,
            and destinations into one tap. Apply any profile when you start or stop recording.
          </div>
        </div>

        {/* My profiles */}
        <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em',
                      padding:'6px 2px 0' }}>MY PROFILES</div>
        {PROFILES.filter(p=>!p.builtin).map(p=>(
          <ProfileCard key={p.id} profile={p} onOpen={setDetailId} />
        ))}

        {/* Built-in */}
        <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em',
                      padding:'6px 2px 0' }}>BUILT-IN</div>
        {PROFILES.filter(p=>p.builtin).map(p=>(
          <ProfileCard key={p.id} profile={p} onOpen={setDetailId} />
        ))}

        {/* Create new */}
        <div onClick={()=>setCreating(true)}
          style={{ background:V2_C.surfHigh, borderRadius:18, padding:14,
                   border:`1.5px dashed ${V2_C.muted}`,
                   display:'flex', alignItems:'center', gap:12, cursor:'pointer' }}>
          <div style={{ width:38, height:38, borderRadius:12, background:V2_C.surfVar,
                        display:'flex', alignItems:'center', justifyContent:'center' }}>
            <V2Ico name="add" size={22} color={V2_C.muted} />
          </div>
          <div>
            <div style={{ fontSize:14, fontWeight:500, color:V2_C.onSurfVar }}>
              Create new profile
            </div>
            <div style={{ fontSize:12, color:V2_C.muted }}>
              Build a custom pipeline from scratch
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Block picker data ────────────────────────────────────────
const BLOCK_CATALOGUE = [
  {
    section: 'Trigger',
    type: 'trigger',
    color: V2_C.amber,
    items: [
      { icon:'schedule',       label:'Time-based',     sub:'Nudge at a set time — e.g. "Start your standup?" at 9 AM',  color:V2_C.amber },
      { icon:'place',          label:'Location',       sub:'Nudge when you arrive — e.g. at the office or studio',       color:V2_C.amber },
      { icon:'calendar_today', label:'Calendar event', sub:'Nudge when a calendar event begins — e.g. a meeting invite', color:V2_C.amber },
      { icon:'wifi',           label:'Network',        sub:'Nudge when you join a specific Wi-Fi network',               color:V2_C.amber },
    ],
  },
  {
    section: 'Mode',
    type: 'mode',
    color: V2_C.primary,
    items: MODES.filter(m=>m.id!=='new').map(m=>({
      icon:m.icon, label:m.label, sub:m.output, color:m.color,
    })),
  },
  {
    section: 'AI Transform',
    type: 'transform',
    color: V2_C.primary,
    items: TRANSFORM_OPTIONS.map(t=>({ ...t, sub:'AI processing step' })),
  },
  {
    section: 'Send to',
    type: 'destination',
    color: V2_C.secondary,
    items: DESTINATION_OPTIONS.map(d=>({ ...d, sub:'Output destination' })),
  },
];

const ALL_PROFILE_ICONS = [
  'groups','lightbulb','checklist','forum','auto_stories',
  'person_search','book','podcast','bolt','stars',
  'school','work','mic','edit_note','flag',
  'rocket_launch','coffee','headphones','camera','brush',
  'sports_esports','psychology','science','biotech','travel_explore',
  'record_voice_over','campaign','tune','hub','token',
];
const PROFILE_COLORS = [
  V2_C.primary, V2_C.secondary, V2_C.tertiary,
  V2_C.amber, V2_C.purple, V2_C.pink, V2_C.onSurfVar,
];

// ── Block picker sheet ───────────────────────────────────────
function BlockPickerSheet({ onPick, onDismiss }) {
  const [search, setSearch] = React.useState('');
  const q = search.toLowerCase();
  return (
    <V2Sheet onDismiss={onDismiss} maxH={560}>
      {/* Header */}
      <div style={{ padding:'4px 16px 10px', flexShrink:0 }}>
        <div style={{ fontSize:15, fontWeight:600, color:V2_C.onSurface }}>Add a block</div>
      </div>

      {/* Search */}
      <div style={{ margin:'0 16px 8px', flexShrink:0,
                    background:V2_C.surfVar, borderRadius:10,
                    display:'flex', alignItems:'center', gap:8, padding:'8px 10px' }}>
        <V2Ico name="search" size={16} color={V2_C.muted} />
        <input value={search} onChange={e=>setSearch(e.target.value)}
          placeholder="Filter blocks…" autoFocus
          style={{ flex:1, background:'none', border:'none', outline:'none',
                   fontSize:13, color:V2_C.onSurface, fontFamily:'DM Sans, sans-serif' }} />
      </div>

      {/* Catalogue */}
      <div style={{ flex:1, overflowY:'auto', paddingBottom:16 }}>
        {BLOCK_CATALOGUE.map(cat=>{
          const items = cat.items.filter(i=>
            !q || i.label.toLowerCase().includes(q) || i.sub.toLowerCase().includes(q));
          if(!items.length) return null;
          return (
            <div key={cat.section}>
              <div style={{ padding:'10px 16px 4px', fontSize:10, color:V2_C.muted,
                            letterSpacing:'0.07em', display:'flex', alignItems:'center', gap:6 }}>
                <div style={{ width:6, height:6, borderRadius:2, background:cat.color }} />
                {cat.section.toUpperCase()}
              </div>

              {/* Trigger explainer */}
              {cat.type==='trigger' && !q && (
                <div style={{ margin:'4px 16px 8px',
                              background:'#1A1400', borderRadius:12,
                              border:`1px solid ${V2_C.amber}22`,
                              padding:'10px 12px', display:'flex', gap:10 }}>
                  <V2Ico name="notifications" size={15} color={V2_C.amber} style={{ marginTop:1 }} />
                  <div>
                    <div style={{ fontSize:12, fontWeight:600, color:V2_C.amber, marginBottom:3 }}>
                      Triggers suggest recording
                    </div>
                    <div style={{ fontSize:11, color:V2_C.onSurfVar, lineHeight:'16px' }}>
                      When the condition is met, Scrybe sends a notification:
                    </div>
                    {/* Notification preview */}
                    <div style={{ marginTop:8, background:V2_C.surfHigh, borderRadius:10,
                                  padding:'8px 10px', display:'flex', gap:8, alignItems:'center' }}>
                      <div style={{ width:24, height:24, borderRadius:7, background:'#091D31',
                                    display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                        <V2Ico name="mic" size={13} color={V2_C.primary} />
                      </div>
                      <div style={{ flex:1 }}>
                        <div style={{ fontSize:11, fontWeight:600, color:V2_C.onSurface }}>
                          Scrybe
                        </div>
                        <div style={{ fontSize:11, color:V2_C.onSurfVar }}>
                          Ready to start your Daily Standup?
                        </div>
                      </div>
                      <div style={{ background:V2_C.primary, borderRadius:6, padding:'3px 7px' }}>
                        <span style={{ fontSize:10, fontWeight:600, color:V2_C.primCont }}>Start</span>
                      </div>
                    </div>
                  </div>
                </div>
              )}
              {items.map((item,i)=>(
                <div key={item.label} onClick={()=>onPick({ ...item, type:cat.type })}
                  style={{ padding:'10px 16px', display:'flex', alignItems:'center', gap:12,
                           cursor:'pointer', borderBottom:`1px solid ${V2_C.border}` }}
                  onMouseEnter={e=>e.currentTarget.style.background=V2_C.surfHigh}
                  onMouseLeave={e=>e.currentTarget.style.background='transparent'}>
                  <div style={{ width:36, height:36, borderRadius:10, flexShrink:0,
                                background:`${item.color}18`,
                                display:'flex', alignItems:'center', justifyContent:'center' }}>
                    <V2Ico name={item.icon} size={18} color={item.color} />
                  </div>
                  <div style={{ flex:1 }}>
                    <div style={{ fontSize:14, color:V2_C.onSurface, fontWeight:500 }}>
                      {item.label}
                    </div>
                    <div style={{ fontSize:11, color:V2_C.muted, marginTop:1 }}>{item.sub}</div>
                  </div>
                  <V2Ico name="add_circle_outline" size={18} color={V2_C.muted} />
                </div>
              ))}
            </div>
          );
        })}
      </div>
    </V2Sheet>
  );
}

// ── Connector with + button ──────────────────────────────────
function PipelineConnector({ onAdd }) {
  return (
    <div style={{ display:'flex', flexDirection:'column', alignItems:'center', gap:0 }}>
      <div style={{ width:2, height:10, background:V2_C.surfVar }} />
      <div onClick={onAdd}
        style={{ width:28, height:28, borderRadius:9999, background:V2_C.surfHigh,
                 border:`1.5px dashed ${V2_C.muted}`,
                 display:'flex', alignItems:'center', justifyContent:'center',
                 cursor:'pointer', zIndex:1 }}
        onMouseEnter={e=>{ e.currentTarget.style.borderColor=V2_C.primary; e.currentTarget.style.background=`${V2_C.primary}18`; }}
        onMouseLeave={e=>{ e.currentTarget.style.borderColor=V2_C.muted; e.currentTarget.style.background=V2_C.surfHigh; }}>
        <V2Ico name="add" size={16} color={V2_C.muted} />
      </div>
      <div style={{ width:2, height:10, background:V2_C.surfVar }} />
    </div>
  );
}

// ── Pipeline block card ──────────────────────────────────────
function PipelineBlock({ block, onRemove }) {
  const TYPE_META = {
    trigger:     { label:'Trigger',   color:V2_C.amber    },
    mode:        { label:'Mode',      color:V2_C.primary  },
    transform:   { label:'Transform', color:V2_C.primary  },
    destination: { label:'Send to',   color:V2_C.secondary},
  };
  const meta = TYPE_META[block.type]||TYPE_META.transform;
  return (
    <div style={{ background:V2_C.surfHigh, borderRadius:16,
                  border:`1px solid ${block.color}33`,
                  padding:'12px 14px', display:'flex', alignItems:'center', gap:12 }}>
      <div style={{ width:40, height:40, borderRadius:12, background:`${block.color}18`,
                    display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
        <V2Ico name={block.icon} size={20} color={block.color} />
      </div>
      <div style={{ flex:1 }}>
        <div style={{ fontSize:10, color:meta.color, fontWeight:600,
                      letterSpacing:'0.06em', marginBottom:2 }}>
          {meta.label.toUpperCase()}
        </div>
        <div style={{ fontSize:14, fontWeight:500, color:V2_C.onSurface }}>{block.label}</div>
        <div style={{ fontSize:11, color:V2_C.muted, marginTop:1 }}>{block.sub}</div>
      </div>
      <div onClick={onRemove} style={{ cursor:'pointer', padding:4, display:'flex' }}>
        <V2Ico name="close" size={18} color={V2_C.muted} />
      </div>
    </div>
  );
}

// ── Create profile screen ────────────────────────────────────
function CreateProfileScreen({ onBack, onSave }) {
  const [step, setStep] = React.useState('name'); // 'name' | 'build'
  const [name, setName] = React.useState('');
  const [icon, setIcon] = React.useState('mic');
  const [color, setColor] = React.useState(V2_C.primary);
  const [blocks, setBlocks] = React.useState([]);
  const [pickerAt, setPickerAt] = React.useState(null); // insert index or null
  const [fromTemplate, setFromTemplate] = React.useState(null);
  const [iconPickerOpen, setIconPickerOpen] = React.useState(false);

  const insertBlock = (block) => {
    setBlocks(prev => {
      const next = [...prev];
      next.splice(pickerAt ?? next.length, 0, { ...block, id: Date.now() });
      return next;
    });
    setPickerAt(null);
  };

  const removeBlock = (idx) => {
    setBlocks(prev => prev.filter((_,i)=>i!==idx));
  };

  const applyTemplate = (profile) => {
    setName(profile.name);
    setIcon(profile.icon);
    setColor(profile.color);
    setBlocks(profile.steps.map((s,i)=>({
      ...s, id:i,
      sub: s.label,
      color: s.color || V2_C.primary,
    })));
    setStep('build');
  };

  /* ── Step 1: Name ── */
  if(step==='name') return (
    <div style={{ background:V2_C.bg, height:'100%', display:'flex',
                  flexDirection:'column', fontFamily:'DM Sans, sans-serif',
                  position:'relative' }}>
      <V2TopBar back onBack={onBack} title="New profile" />

      <div style={{ flex:1, overflowY:'auto', padding:'0 14px 16px',
                    display:'flex', flexDirection:'column', gap:12 }}>

        {/* Name input */}
        <div style={{ background:V2_C.surfHigh, borderRadius:16,
                      border:`1px solid ${V2_C.border}`, overflow:'hidden' }}>
          <input value={name} onChange={e=>setName(e.target.value)}
            placeholder="Profile name…"
            style={{ display:'block', width:'100%', padding:'14px 16px',
                     background:'none', border:'none', outline:'none',
                     fontSize:17, fontWeight:500, color:V2_C.onSurface,
                     fontFamily:'DM Sans, sans-serif' }} />
        </div>

        {/* Icon + color in one compact card */}
        <div style={{ background:V2_C.surfHigh, borderRadius:16,
                      border:`1px solid ${V2_C.border}`, overflow:'hidden' }}>

          {/* Icon — horizontal scroll row with "more" at end */}
          <div style={{ padding:'10px 14px 0' }}>
            <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em', marginBottom:8 }}>
              ICON
            </div>
            <div style={{ display:'flex', gap:8, overflowX:'auto',
                          scrollbarWidth:'none', paddingBottom:10 }}>
              {ALL_PROFILE_ICONS.slice(0, 8).map(ic=>(
                <div key={ic} onClick={()=>setIcon(ic)} style={{ flexShrink:0 }}>
                  <div style={{ width:40, height:40, borderRadius:12, cursor:'pointer',
                               background: icon===ic ? `${color}22` : V2_C.surfVar,
                               border:`2px solid ${icon===ic?color:'transparent'}`,
                               display:'flex', alignItems:'center', justifyContent:'center' }}>
                    <V2Ico name={ic} size={20} color={icon===ic?color:V2_C.muted} />
                  </div>
                </div>
              ))}
              {/* "More icons" button */}
              <div onClick={()=>setIconPickerOpen(true)} style={{ flexShrink:0 }}>
                <div style={{ width:40, height:40, borderRadius:12, cursor:'pointer',
                              background: !ALL_PROFILE_ICONS.slice(0,8).includes(icon) ? `${color}22` : V2_C.surfVar,
                              border:`2px solid ${ !ALL_PROFILE_ICONS.slice(0,8).includes(icon) ? color : V2_C.border}`,
                              display:'flex', flexDirection:'column',
                              alignItems:'center', justifyContent:'center', gap:1 }}>
                  {!ALL_PROFILE_ICONS.slice(0,8).includes(icon)
                    ? <V2Ico name={icon} size={20} color={color} />
                    : <>
                        <div style={{ display:'flex', gap:2 }}>
                          <div style={{ width:4, height:4, borderRadius:1, background:V2_C.muted }} />
                          <div style={{ width:4, height:4, borderRadius:1, background:V2_C.muted }} />
                        </div>
                        <div style={{ display:'flex', gap:2 }}>
                          <div style={{ width:4, height:4, borderRadius:1, background:V2_C.muted }} />
                          <div style={{ width:4, height:4, borderRadius:1, background:V2_C.muted }} />
                        </div>
                      </>
                  }
                </div>
              </div>
            </div>
          </div>

          <div style={{ height:1, background:V2_C.border }} />

          {/* Color — single row */}
          <div style={{ padding:'10px 14px 12px' }}>
            <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em', marginBottom:8 }}>
              COLOR
            </div>
            <div style={{ display:'flex', gap:10 }}>
              {PROFILE_COLORS.map(c=>(
                <div key={c} onClick={()=>setColor(c)}
                  style={{ width:30, height:30, borderRadius:'50%', cursor:'pointer',
                           background:c, flexShrink:0,
                           outline: color===c ? `3px solid ${c}` : 'none',
                           outlineOffset: 2 }} />
              ))}
            </div>
          </div>
        </div>

        {/* Templates — compact rows */}
        <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em',
                      padding:'2px 2px 0' }}>START FROM A TEMPLATE</div>
        <div style={{ background:V2_C.surfHigh, borderRadius:16,
                      border:`1px solid ${V2_C.border}`, overflow:'hidden' }}>
          {PROFILES.slice(0,4).map((p,i)=>(
            <div key={p.id} onClick={()=>applyTemplate(p)}
              style={{ padding:'11px 14px', display:'flex', alignItems:'center', gap:12,
                       cursor:'pointer',
                       borderBottom:i<3?`1px solid ${V2_C.border}`:'none' }}
              onMouseEnter={e=>e.currentTarget.style.background=V2_C.surfVar}
              onMouseLeave={e=>e.currentTarget.style.background='transparent'}>
              <div style={{ width:34, height:34, borderRadius:10, background:p.bg,
                            display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                <V2Ico name={p.icon} size={17} color={p.color} />
              </div>
              <div style={{ flex:1 }}>
                <div style={{ fontSize:14, color:V2_C.onSurface }}>{p.name}</div>
                <div style={{ fontSize:11, color:V2_C.muted }}>
                  {p.steps.length} blocks · {p.mode}
                </div>
              </div>
              <V2Ico name="chevron_right" size={16} color={V2_C.muted} />
            </div>
          ))}
        </div>
      </div>

      <div style={{ padding:'0 14px 24px', flexShrink:0,
                    borderTop:`1px solid ${V2_C.border}`,
                    paddingTop:12, background:V2_C.bg }}>
        <button
          onClick={()=>setStep('build')}
          disabled={!name.trim()}
          style={{ width:'100%', background:name.trim()?V2_C.primary:V2_C.surfVar,
                   border:'none', borderRadius:14, padding:'14px 0',
                   fontSize:15, fontWeight:600,
                   color:name.trim()?V2_C.primCont:V2_C.muted,
                   cursor:name.trim()?'pointer':'default',
                   fontFamily:'DM Sans, sans-serif' }}>
          Continue →
        </button>
      </div>

      {/* Icon picker modal — outside scroll, overlays full screen */}
      {iconPickerOpen && (
        <div style={{ position:'absolute', inset:0, zIndex:200 }}
             onClick={()=>setIconPickerOpen(false)}>
          <div style={{ position:'absolute', inset:0, background:'rgba(0,0,0,0.72)' }} />
          <div onClick={e=>e.stopPropagation()}
            style={{ position:'absolute', bottom:0, left:0, right:0,
                     background:V2_C.surface, borderRadius:'22px 22px 0 0',
                     border:`1px solid ${V2_C.border}`, borderBottom:'none',
                     padding:'16px 16px 32px',
                     display:'flex', flexDirection:'column', gap:14 }}>
            <div style={{ display:'flex', justifyContent:'center' }}>
              <div style={{ width:36, height:4, borderRadius:2, background:V2_C.surfVar }} />
            </div>
            <div style={{ fontSize:15, fontWeight:600, color:V2_C.onSurface }}>
              Choose an icon
            </div>
            <div style={{ display:'flex', flexWrap:'wrap', gap:10 }}>
              {ALL_PROFILE_ICONS.map(ic=>(
                <div key={ic} onClick={()=>{ setIcon(ic); setIconPickerOpen(false); }}>
                  <div style={{ width:48, height:48, borderRadius:14, cursor:'pointer',
                               background: icon===ic ? `${color}22` : V2_C.surfHigh,
                               border:`2px solid ${icon===ic?color:'transparent'}`,
                               display:'flex', alignItems:'center', justifyContent:'center' }}>
                    <V2Ico name={ic} size={24} color={icon===ic?color:V2_C.muted} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );

  /* ── Step 2: Pipeline builder ── */
  return (
    <div style={{ background:V2_C.bg, height:'100%', display:'flex',
                  flexDirection:'column', fontFamily:'DM Sans, sans-serif',
                  position:'relative' }}>

      <V2TopBar back onBack={()=>setStep('name')}
        title={name || 'Build pipeline'}
        sub={`${blocks.length} block${blocks.length!==1?'s':''}`}
        right={
          <div style={{ display:'flex', alignItems:'center', gap:4 }}>
            <div style={{ width:28, height:28, borderRadius:9, background:`${color}18`,
                          display:'flex', alignItems:'center', justifyContent:'center' }}>
              <V2Ico name={icon} size={16} color={color} />
            </div>
            <div style={{ padding:6 }}><V2Ico name="more_vert" /></div>
          </div>
        }
      />

      <div style={{ flex:1, overflowY:'auto', padding:'4px 14px 100px',
                    display:'flex', flexDirection:'column', alignItems:'stretch' }}>

        {blocks.length===0 ? (
          /* Empty state */
          <div style={{ flex:1, display:'flex', flexDirection:'column',
                        alignItems:'center', justifyContent:'center', gap:16, padding:'40px 0' }}>
            <div style={{ width:56, height:56, borderRadius:18, background:V2_C.surfHigh,
                          display:'flex', alignItems:'center', justifyContent:'center' }}>
              <V2Ico name="account_tree" size={28} color={V2_C.muted} />
            </div>
            <div style={{ textAlign:'center' }}>
              <div style={{ fontSize:15, fontWeight:600, color:V2_C.onSurface }}>
                No blocks yet
              </div>
              <div style={{ fontSize:13, color:V2_C.muted, marginTop:4 }}>
                Tap + below to add your first block
              </div>
            </div>
            <div onClick={()=>setPickerAt(0)}
              style={{ background:V2_C.primary, borderRadius:12, padding:'11px 24px',
                       display:'flex', alignItems:'center', gap:8, cursor:'pointer' }}>
              <V2Ico name="add" size={18} color={V2_C.primCont} />
              <span style={{ fontSize:14, fontWeight:600, color:V2_C.primCont }}>
                Add first block
              </span>
            </div>
          </div>
        ) : (
          <div style={{ display:'flex', flexDirection:'column', alignItems:'stretch',
                        paddingTop:8 }}>
            {/* First connector */}
            <div style={{ display:'flex', justifyContent:'center' }}>
              <PipelineConnector onAdd={()=>setPickerAt(0)} />
            </div>

            {blocks.map((block, idx)=>(
              <React.Fragment key={block.id}>
                <PipelineBlock block={block} onRemove={()=>removeBlock(idx)} />
                <div style={{ display:'flex', justifyContent:'center' }}>
                  <PipelineConnector onAdd={()=>setPickerAt(idx+1)} />
                </div>
              </React.Fragment>
            ))}
          </div>
        )}
      </div>

      {/* Bottom bar */}
      <div style={{ position:'absolute', bottom:0, left:0, right:0,
                    background:V2_C.surface, borderTop:`1px solid ${V2_C.border}`,
                    padding:'10px 14px 24px', display:'flex', gap:8 }}>
        <div onClick={()=>setPickerAt(blocks.length)}
          style={{ flex:1, background:V2_C.surfHigh, border:`1.5px dashed ${V2_C.muted}`,
                   borderRadius:14, padding:'12px 0',
                   display:'flex', alignItems:'center', justifyContent:'center',
                   gap:8, cursor:'pointer' }}>
          <V2Ico name="add" size={18} color={V2_C.muted} />
          <span style={{ fontSize:14, color:V2_C.onSurfVar }}>Add block</span>
        </div>
        <button
          onClick={onSave}
          disabled={blocks.length===0}
          style={{ flex:2, background:blocks.length?V2_C.primary:V2_C.surfVar,
                   border:'none', borderRadius:14, padding:'12px 0',
                   fontSize:14, fontWeight:600,
                   color:blocks.length?V2_C.primCont:V2_C.muted,
                   cursor:blocks.length?'pointer':'default',
                   fontFamily:'DM Sans, sans-serif' }}>
          Save profile
        </button>
      </div>

      {/* Block picker sheet */}
      {pickerAt!==null && (
        <BlockPickerSheet
          onPick={insertBlock}
          onDismiss={()=>setPickerAt(null)}
        />
      )}
    </div>
  );
}

Object.assign(window, { ProfilesScreen, ProfileDetail, CreateProfileScreen, PROFILES });
