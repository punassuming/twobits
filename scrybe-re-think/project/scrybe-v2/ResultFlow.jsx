// scrybe-v2/ResultFlow.jsx — Post-capture session view + ecosystem actions

// ── Ecosystem integration sheet ────────────────────────────────
const INTEGRATIONS = [
  { id:'calendar', icon:'event',         label:'Add to Calendar',    sub:'3 tasks have dates',     color:'#4285F4' },
  { id:'reminders',icon:'notifications', label:'Set Reminders',      sub:'2 due this week',        color:'#FF5252' },
  { id:'notion',   icon:'article',       label:'Export to Notion',   sub:'Paste as linked note',   color:'#E2E8F0' },
  { id:'slack',    icon:'chat',          label:'Share in Slack',     sub:'Send summary to channel',color:'#E01E5A' },
  { id:'mail',     icon:'mail',          label:'Email summary',      sub:'Send to attendees',      color:V2_C.tertiary },
  { id:'shortcuts',icon:'bolt',          label:'Run Shortcut',       sub:'iOS Shortcuts automation',color:V2_C.amber },
  { id:'share',    icon:'ios_share',     label:'Share sheet',        sub:'Any app via share sheet',color:V2_C.onSurfVar },
];

function EcosystemSheet({ onDismiss }) {
  return (
    <V2Sheet onDismiss={onDismiss} maxH={500}>
      <div style={{ padding:'4px 16px 20px', display:'flex', flexDirection:'column', gap:12,
                    flex:1, overflowY:'auto' }}>
        <div>
          <div style={{ fontSize:15, fontWeight:600, color:V2_C.onSurface }}>Send to…</div>
          <div style={{ fontSize:12, color:V2_C.muted, marginTop:2 }}>
            Push this session's output to any app
          </div>
        </div>

        {INTEGRATIONS.map(int=>(
          <div key={int.id}
            style={{ background:V2_C.surfHigh, borderRadius:14, padding:'11px 14px',
                     display:'flex', alignItems:'center', gap:12, cursor:'pointer',
                     border:`1px solid ${V2_C.border}` }}>
            <div style={{ width:38, height:38, borderRadius:10, background:V2_C.surfVar,
                          display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
              <V2Ico name={int.icon} size={20} color={int.color} />
            </div>
            <div style={{ flex:1 }}>
              <div style={{ fontSize:14, color:V2_C.onSurface, fontWeight:500 }}>{int.label}</div>
              <div style={{ fontSize:11, color:V2_C.muted, marginTop:1 }}>{int.sub}</div>
            </div>
            <V2Ico name="chevron_right" size={16} color={V2_C.muted} />
          </div>
        ))}
      </div>
    </V2Sheet>
  );
}

// ── Transcript with speaker attribution ──────────────────────
function TranscriptView() {
  return (
    <div style={{ display:'flex', flexDirection:'column', gap:12 }}>
      {LIVE_LINES.map((line,i)=>{
        const sp = SPEAKERS[line.s]||SPEAKERS[0];
        return (
          <div key={i} style={{ display:'flex', gap:10 }}>
            <div style={{ width:3, borderRadius:2, background:sp.color, flexShrink:0 }} />
            <div>
              <span style={{ fontSize:11, color:sp.color, fontWeight:500, display:'block',
                             marginBottom:3 }}>{sp.label}</span>
              <span style={{ fontSize:13, color:V2_C.onSurface, lineHeight:'20px' }}>
                {line.text}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ── Task item ───────────────────────────────────────────────
function TaskItem({ task }) {
  const [done, setDone] = React.useState(task.done);
  return (
    <div style={{ display:'flex', gap:10, alignItems:'flex-start', padding:'1px 0' }}>
      <div onClick={()=>setDone(d=>!d)}
        style={{ width:18, height:18, borderRadius:5, marginTop:1,
                 border:`1.5px solid ${done?V2_C.secondary:V2_C.surfVar}`,
                 background:done?'#0A2018':'transparent',
                 display:'flex', alignItems:'center', justifyContent:'center',
                 cursor:'pointer', flexShrink:0 }}>
        {done && <V2Ico name="check" size={12} color={V2_C.secondary} />}
      </div>
      <div style={{ flex:1 }}>
        <div style={{ fontSize:13, color:done?V2_C.muted:V2_C.onSurface, lineHeight:'19px',
                      textDecoration:done?'line-through':'none' }}>
          {task.text}
        </div>
        <V2Row style={{ gap:8, marginTop:3, flexWrap:'wrap' }}>
          {task.assignee && (
            <V2Row style={{ gap:3 }}>
              <V2Ico name="person" size={11} color={V2_C.muted} />
              <span style={{ fontSize:11, color:V2_C.muted }}>{task.assignee}</span>
            </V2Row>
          )}
          {task.due && (
            <V2Row style={{ gap:3 }}>
              <V2Ico name="schedule" size={11} color={V2_C.amber} />
              <span style={{ fontSize:11, color:V2_C.amber }}>{task.due}</span>
            </V2Row>
          )}
        </V2Row>
      </div>
    </div>
  );
}

// ── Tag creation workflow ───────────────────────────────────
const AI_SUGGESTED_TAGS = ['api-integration','q3-planning','dedup','schema','dana','james','blocker','sprint'];
const RECENT_TAGS = ['weekly','review','standup','ux','product','design','backend','sync','async','okrs'];

function TagsRow({ tags: initialTags }) {
  const [tags, setTags] = React.useState(initialTags);
  const [adding, setAdding] = React.useState(false);
  const [query, setQuery] = React.useState('');
  const inputRef = React.useRef(null);

  React.useEffect(()=>{
    if(adding && inputRef.current) inputRef.current.focus();
  },[adding]);

  const removeTag = t => setTags(prev=>prev.filter(x=>x!==t));

  const addTag = t => {
    const clean = t.replace(/^#/,'').toLowerCase().trim();
    if(!clean || tags.includes(clean)) return;
    setTags(prev=>[...prev, clean]);
    setQuery('');
  };

  const confirmNew = () => {
    if(query.trim()) { addTag(query); setQuery(''); }
    else setAdding(false);
  };

  // Filter suggestions
  const q = query.toLowerCase().trim();
  const filteredAI = AI_SUGGESTED_TAGS.filter(t=>
    !tags.includes(t) && (!q || t.includes(q)));
  const filteredRecent = RECENT_TAGS.filter(t=>
    !tags.includes(t) && !AI_SUGGESTED_TAGS.includes(t) && (!q || t.includes(q)));
  const canCreate = q && !tags.includes(q) && !AI_SUGGESTED_TAGS.includes(q) && !RECENT_TAGS.includes(q);

  return (
    <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
      {/* Existing tags + add button */}
      <div style={{ display:'flex', flexWrap:'wrap', gap:6, alignItems:'center' }}>
        {tags.map(t=>(
          <div key={t} style={{ background:V2_C.surfVar, borderRadius:9999,
                                 padding:'5px 10px', display:'flex', alignItems:'center', gap:5 }}>
            <span style={{ fontSize:13, color:V2_C.onSurface }}>#{t}</span>
            <div onClick={()=>removeTag(t)} style={{ cursor:'pointer', display:'flex',
                                                       lineHeight:0, padding:'1px' }}>
              <V2Ico name="close" size={13} color={V2_C.muted} />
            </div>
          </div>
        ))}
        <div onClick={()=>setAdding(v=>!v)}
          style={{ background: adding?`${V2_C.primary}18`:V2_C.surfHigh,
                   border:`1.5px ${adding?'solid':'dashed'} ${adding?V2_C.primary+'55':V2_C.muted}`,
                   borderRadius:9999, padding:'5px 10px',
                   display:'flex', alignItems:'center', gap:4, cursor:'pointer' }}>
          <V2Ico name={adding?'close':'add'} size={13} color={adding?V2_C.primary:V2_C.muted} />
          <span style={{ fontSize:13, color:adding?V2_C.primary:V2_C.muted, fontWeight:adding?500:400 }}>
            {adding ? 'Done' : 'Add tag'}
          </span>
        </div>
      </div>

      {/* Inline tag creation panel */}
      {adding && (
        <div style={{ background:V2_C.surfVar, borderRadius:14,
                      border:`1px solid ${V2_C.border}`,
                      display:'flex', flexDirection:'column', gap:0, overflow:'hidden' }}>

          {/* Search input */}
          <div style={{ display:'flex', alignItems:'center', gap:8,
                        padding:'10px 12px',
                        borderBottom:`1px solid ${V2_C.border}` }}>
            <V2Ico name="search" size={16} color={V2_C.muted} />
            <input
              ref={inputRef}
              value={query}
              onChange={e=>setQuery(e.target.value)}
              onKeyDown={e=>{ if(e.key==='Enter') { addTag(query); } }}
              placeholder="Search or create a tag…"
              style={{ flex:1, background:'none', border:'none', outline:'none',
                       fontSize:14, color:V2_C.onSurface,
                       fontFamily:'DM Sans, sans-serif' }}
            />
            {query && (
              <div onClick={()=>setQuery('')} style={{ cursor:'pointer', display:'flex' }}>
                <V2Ico name="close" size={15} color={V2_C.muted} />
              </div>
            )}
          </div>

          <div style={{ maxHeight:220, overflowY:'auto' }}>

            {/* Create new */}
            {canCreate && (
              <div onClick={()=>addTag(query)}
                style={{ padding:'10px 12px', display:'flex', alignItems:'center', gap:10,
                         borderBottom:`1px solid ${V2_C.border}`, cursor:'pointer',
                         background:`${V2_C.primary}0D` }}>
                <div style={{ width:28, height:28, borderRadius:8,
                              background:`${V2_C.primary}20`,
                              display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
                  <V2Ico name="add" size={15} color={V2_C.primary} />
                </div>
                <span style={{ fontSize:13, color:V2_C.primary }}>
                  Create <strong style={{ fontWeight:600 }}>#{query}</strong>
                </span>
              </div>
            )}

            {/* AI suggested */}
            {filteredAI.length>0 && (
              <>
                <div style={{ padding:'8px 12px 4px', fontSize:10, color:V2_C.muted,
                              letterSpacing:'0.07em', display:'flex', alignItems:'center', gap:5 }}>
                  <V2Ico name="auto_awesome" size={11} color={V2_C.primary} />
                  AI SUGGESTED
                </div>
                {filteredAI.map(t=>(
                  <div key={t} onClick={()=>addTag(t)}
                    style={{ padding:'9px 12px', display:'flex', alignItems:'center',
                             gap:10, cursor:'pointer',
                             borderBottom:`1px solid ${V2_C.border}` }}
                    onMouseEnter={e=>e.currentTarget.style.background=V2_C.surfHigh}
                    onMouseLeave={e=>e.currentTarget.style.background='transparent'}>
                    <V2Ico name="label" size={15} color={V2_C.primary} />
                    <span style={{ fontSize:13, color:V2_C.onSurface, flex:1 }}>#{t}</span>
                    <V2Ico name="add_circle_outline" size={16} color={V2_C.muted} />
                  </div>
                ))}
              </>
            )}

            {/* Recently used */}
            {filteredRecent.length>0 && (
              <>
                <div style={{ padding:'8px 12px 4px', fontSize:10, color:V2_C.muted,
                              letterSpacing:'0.07em' }}>RECENTLY USED</div>
                {filteredRecent.slice(0,6).map(t=>(
                  <div key={t} onClick={()=>addTag(t)}
                    style={{ padding:'9px 12px', display:'flex', alignItems:'center',
                             gap:10, cursor:'pointer',
                             borderBottom:`1px solid ${V2_C.border}` }}
                    onMouseEnter={e=>e.currentTarget.style.background=V2_C.surfHigh}
                    onMouseLeave={e=>e.currentTarget.style.background='transparent'}>
                    <V2Ico name="history" size={15} color={V2_C.muted} />
                    <span style={{ fontSize:13, color:V2_C.onSurfVar, flex:1 }}>#{t}</span>
                    <V2Ico name="add_circle_outline" size={16} color={V2_C.muted} />
                  </div>
                ))}
              </>
            )}

            {/* Empty state */}
            {!canCreate && filteredAI.length===0 && filteredRecent.length===0 && (
              <div style={{ padding:'20px 12px', textAlign:'center' }}>
                <span style={{ fontSize:13, color:V2_C.muted }}>No matching tags</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// ── Metadata strip ───────────────────────────────────────────
function MetaStrip({ session }) {
  return (
    <V2Row style={{ gap:14, flexWrap:'wrap', padding:'2px 0' }}>
      <V2Row style={{ gap:4 }}>
        <V2Ico name="place" size={13} color={V2_C.muted} />
        <span style={{ fontSize:12, color:V2_C.muted }}>{session.location}</span>
      </V2Row>
      <V2Row style={{ gap:4 }}>
        <V2Ico name="record_voice_over" size={13} color={V2_C.muted} />
        <span style={{ fontSize:12, color:V2_C.muted }}>{session.speakers} speakers</span>
      </V2Row>
      <V2Row style={{ gap:4 }}>
        <V2Ico name="timer" size={13} color={V2_C.muted} />
        <span style={{ fontSize:12, color:V2_C.muted }}>{session.dur}</span>
      </V2Row>
    </V2Row>
  );
}

// ── More menu sheet ──────────────────────────────────────────
function MoreMenuSheet({ session, onDismiss, onDelete }) {
  const mode = MODES.find(m=>m.id===session.mode)||MODES[0];

  const groups = [
    {
      items: [
        { icon:'drive_file_rename_outline', label:'Rename',            color:V2_C.onSurface },
        { icon:'folder_open',               label:'Move to folder',    color:V2_C.onSurface },
        { icon:'push_pin',                  label:'Pin to top',        color:V2_C.onSurface },
        { icon:'bookmark_add',              label:'Mark as important', color:V2_C.amber     },
      ],
    },
    {
      label: 'Re-process',
      items: [
        { icon:mode.icon,          label:`Re-run as ${mode.label}`, color:mode.color   },
        { icon:'swap_horiz',       label:'Change mode…',            color:V2_C.primary },
        { icon:'tune',             label:'Apply a profile…',        color:V2_C.primary },
      ],
    },
    {
      label: 'Export',
      items: [
        { icon:'download',         label:'Export audio file',       color:V2_C.onSurface },
        { icon:'content_copy',     label:'Copy transcript',         color:V2_C.onSurface },
        { icon:'picture_as_pdf',   label:'Export as PDF',           color:V2_C.tertiary  },
      ],
    },
    {
      label: 'Manage',
      items: [
        { icon:'archive',          label:'Archive session',         color:V2_C.muted     },
        { icon:'delete_outline',   label:'Delete session',          color:'#FF6B6B'      },
      ],
    },
  ];

  return (
    <V2Sheet onDismiss={onDismiss} maxH={580}>
      <div style={{ padding:'0 8px 28px', flex:1, overflowY:'auto' }}>
        {/* Session identity */}
        <div style={{ padding:'4px 10px 12px',
                      borderBottom:`1px solid ${V2_C.border}`, marginBottom:8 }}>
          <div style={{ fontSize:14, fontWeight:600, color:V2_C.onSurface,
                        overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
            {session.title}
          </div>
          <V2Row style={{ gap:6, marginTop:4 }}>
            <V2ModeBadge modeId={session.mode} />
            <span style={{ fontSize:11, color:V2_C.muted }}>{session.time}</span>
          </V2Row>
        </div>

        {groups.map((grp, gi)=>(
          <div key={gi} style={{ marginBottom:4 }}>
            {grp.label && (
              <div style={{ fontSize:10, color:V2_C.muted, letterSpacing:'0.07em',
                            padding:'8px 10px 4px' }}>{grp.label.toUpperCase()}</div>
            )}
            {grp.items.map((item, ii)=>(
              <div key={ii} onClick={onDismiss}
                style={{ display:'flex', alignItems:'center', gap:12,
                         padding:'11px 10px', borderRadius:12, cursor:'pointer' }}
                onMouseEnter={e=>e.currentTarget.style.background=V2_C.surfHigh}
                onMouseLeave={e=>e.currentTarget.style.background='transparent'}>
                <V2Ico name={item.icon} size={20} color={item.color} />
                <span style={{ fontSize:14, color:item.color }}>{item.label}</span>
              </div>
            ))}
            {gi < groups.length-1 && (
              <div style={{ height:1, background:V2_C.border, margin:'4px 10px' }} />
            )}
          </div>
        ))}
      </div>
    </V2Sheet>
  );
}

// ── Main result screen ───────────────────────────────────────
function ResultScreen({ sessionId, onBack }) {
  const session = SESSIONS.find(s=>s.id===sessionId)||SESSIONS[0];
  const [playing, setPlaying] = React.useState(false);
  const [pos, setPos] = React.useState(0);
  const [ecosystemOpen, setEcosystemOpen] = React.useState(false);
  const [moreOpen, setMoreOpen] = React.useState(false);
  const [tab, setTab] = React.useState('output'); // 'output' | 'transcript' | 'tasks'

  React.useEffect(()=>{
    if(!playing) return;
    const id = setInterval(()=>setPos(p=>{ if(p>=1){setPlaying(false);return 1;} return p+0.005; }), 100);
    return ()=>clearInterval(id);
  },[playing]);

  return (
    <div style={{ background:V2_C.bg, height:'100%', display:'flex',
                  flexDirection:'column', fontFamily:'DM Sans, sans-serif',
                  position:'relative' }}>

      <V2TopBar
        back
        onBack={onBack}
        title={session.title}
        sub={`${session.time} · ${session.dur}`}
        right={
          <V2Row>
            <div onClick={()=>setEcosystemOpen(true)}
              style={{ padding:8, cursor:'pointer', display:'flex' }}>
              <V2Ico name="ios_share" />
            </div>
            <div onClick={()=>setMoreOpen(true)}
              style={{ padding:8, cursor:'pointer', display:'flex' }}>
              <V2Ico name="more_vert" />
            </div>
          </V2Row>
        }
      />

      <div style={{ flex:1, overflowY:'auto', display:'flex',
                    flexDirection:'column', gap:10, padding:'0 14px 20px' }}>

        {/* Mode + meta */}
        <V2Row style={{ gap:8, flexWrap:'wrap' }}>
          <V2ModeBadge modeId={session.mode} size="md" />
          <MetaStrip session={session} />
        </V2Row>

        {/* Audio player */}
        <V2Card style={{ gap:10 }}>
          <V2WaveSpeaker h={44} position={pos} />
          {/* Speaker legend */}
          <V2Row style={{ gap:10 }}>
            {SPEAKERS.map(sp=>(
              <V2Row key={sp.id} style={{ gap:4 }}>
                <div style={{ width:6, height:6, borderRadius:'50%', background:sp.color }} />
                <span style={{ fontSize:10, color:V2_C.muted }}>{sp.label}</span>
              </V2Row>
            ))}
          </V2Row>
          <V2Row style={{ justifyContent:'center', gap:16 }}>
            <div onClick={()=>setPos(p=>Math.max(0,p-0.1))}
              style={{ cursor:'pointer', display:'flex' }}>
              <V2Ico name="replay_10" size={24} color={V2_C.onSurfVar} />
            </div>
            <div onClick={()=>setPlaying(p=>!p)}
              style={{ width:44, height:44, borderRadius:'50%', background:V2_C.primary,
                       display:'flex', alignItems:'center', justifyContent:'center',
                       cursor:'pointer', boxShadow:`0 2px 16px ${V2_C.primary}44` }}>
              <V2Ico name={playing?'pause':'play_arrow'} size={24} color={V2_C.primCont} />
            </div>
            <div onClick={()=>setPos(p=>Math.min(1,p+0.1))}
              style={{ cursor:'pointer', display:'flex' }}>
              <V2Ico name="forward_10" size={24} color={V2_C.onSurfVar} />
            </div>
          </V2Row>
        </V2Card>

        {/* Tab strip */}
        <div style={{ display:'flex', background:V2_C.surfVar, borderRadius:12,
                      padding:3, gap:2 }}>
          {[['output','auto_awesome','Output'],
            ['tasks','checklist','Tasks'],
            ['transcript','notes','Transcript']].map(([id,ico,lbl])=>(
            <div key={id} onClick={()=>setTab(id)}
              style={{ flex:1, display:'flex', alignItems:'center', justifyContent:'center',
                       gap:5, padding:'7px 0', borderRadius:10, cursor:'pointer',
                       background:tab===id?V2_C.surfHigh:'transparent' }}>
              <V2Ico name={ico} size={14} color={tab===id?V2_C.primary:V2_C.muted} />
              <span style={{ fontSize:12, fontWeight:tab===id?600:400,
                             color:tab===id?V2_C.primary:V2_C.muted }}>{lbl}</span>
            </div>
          ))}
        </div>

        {/* Tab: Output */}
        {tab==='output' && (
          <div style={{ display:'flex', flexDirection:'column', gap:10 }}>
            <V2Card>
              <V2Row style={{ gap:6 }}>
                <V2Ico name="summarize" size={14} color={V2_C.primary} />
                <span style={{ fontSize:13, fontWeight:600, color:V2_C.onSurface }}>Summary</span>
              </V2Row>
              <div style={{ fontSize:13, color:V2_C.onSurface, lineHeight:'20px' }}>
                API integration timeline and dedup sync were the main blockers.
                James will draft schema by Thursday; Dana reviews by Friday.
                Dedup sync pushed to next sprint.
              </div>
            </V2Card>

            {/* Extended actions */}
            <div style={{ fontSize:11, color:V2_C.muted, letterSpacing:'0.06em', padding:'4px 2px 0' }}>
              EXTENDED ACTIONS
            </div>
            {[
              { icon:'auto_fix_high', label:'Clean up transcript',      color:V2_C.primary,   bg:'#091D31' },
              { icon:'psychology',    label:'Analyze sentiment',        color:V2_C.purple,    bg:'#130E22' },
              { icon:'translate',     label:'Translate',                color:V2_C.amber,     bg:'#1A1400' },
              { icon:'create_new_folder', label:'Add to folder',        color:V2_C.onSurfVar, bg:V2_C.surfHigh },
            ].map(a=>(
              <div key={a.label}
                style={{ background:a.bg, borderRadius:13, padding:'12px 14px',
                         display:'flex', alignItems:'center', gap:12, cursor:'pointer',
                         border:`1px solid ${a.color==='#8B9BAB'?V2_C.border:a.color+'22'}` }}>
                <V2Ico name={a.icon} size={18} color={a.color} />
                <span style={{ fontSize:14, color:a.color, flex:1, fontWeight:500 }}>{a.label}</span>
                <V2Ico name="chevron_right" size={16} color={V2_C.muted} />
              </div>
            ))}

            {/* Tags */}
            <V2Card>
              <V2Row style={{ gap:6, marginBottom:2 }}>
                <V2Ico name="label" size={14} color={V2_C.muted} />
                <span style={{ fontSize:13, fontWeight:600, color:V2_C.onSurface }}>Tags</span>
              </V2Row>
              <TagsRow tags={session.tags} />
            </V2Card>
          </div>
        )}

        {/* Tab: Tasks — always required */}
        {tab==='tasks' && (
          <V2Card style={{ gap:12 }}>
            <V2Row style={{ justifyContent:'space-between' }}>
              <V2Row style={{ gap:6 }}>
                <V2Ico name="checklist" size={15} color={V2_C.secondary} />
                <span style={{ fontSize:13, fontWeight:600, color:V2_C.onSurface }}>Tasks</span>
                <div style={{ background:'#0A2018', borderRadius:9999, padding:'1px 7px' }}>
                  <span style={{ fontSize:11, color:V2_C.secondary }}>
                    {EXTRACTED_TASKS.length}
                  </span>
                </div>
              </V2Row>
              <div onClick={()=>setEcosystemOpen(true)}
                style={{ background:V2_C.surfVar, borderRadius:9999,
                          padding:'4px 10px', cursor:'pointer' }}>
                <span style={{ fontSize:12, color:V2_C.onSurfVar }}>Export →</span>
              </div>
            </V2Row>
            {EXTRACTED_TASKS.map(t=><TaskItem key={t.id} task={t} />)}
            {/* Add task */}
            <div style={{ display:'flex', gap:8, alignItems:'center', paddingTop:4,
                          borderTop:`1px solid ${V2_C.border}` }}>
              <V2Ico name="add_circle_outline" size={18} color={V2_C.muted} />
              <span style={{ fontSize:13, color:V2_C.muted }}>Add task</span>
            </div>
          </V2Card>
        )}

        {/* Tab: Transcript */}
        {tab==='transcript' && (
          <V2Card>
            <V2Row style={{ justifyContent:'space-between' }}>
              <V2Row style={{ gap:6 }}>
                <V2Ico name="notes" size={14} color={V2_C.onSurfVar} />
                <span style={{ fontSize:13, fontWeight:600, color:V2_C.onSurface }}>Transcript</span>
              </V2Row>
              <V2Row style={{ gap:4 }}>
                <V2Ico name="content_copy" size={18} color={V2_C.muted} style={{ cursor:'pointer', padding:4 }} />
                <V2Ico name="drive_file_rename_outline" size={18} color={V2_C.muted} style={{ cursor:'pointer', padding:4 }} />
              </V2Row>
            </V2Row>
            <TranscriptView />
          </V2Card>
        )}
      </div>

      {/* Ecosystem sheet overlay */}
      {ecosystemOpen && <EcosystemSheet onDismiss={()=>setEcosystemOpen(false)} />}

      {/* More menu overlay */}
      {moreOpen && (
        <MoreMenuSheet
          session={session}
          onDismiss={()=>setMoreOpen(false)}
        />
      )}
    </div>
  );
}

Object.assign(window, { ResultScreen, EcosystemSheet, MoreMenuSheet });
