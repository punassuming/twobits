// scrybe-v2/HomeScreen.jsx — Rich metadata feed, Google-Photos-for-audio

function HomeSessionCard({ session, onOpen }) {
  const mode = MODES.find(m=>m.id===session.mode)||MODES[0];
  return (
    <V2Card onClick={()=>onOpen(session.id)} style={{ cursor:'pointer', gap:10 }}>
      {/* Row 1: mode + title */}
      <V2Row style={{ alignItems:'flex-start', gap:10 }}>
        <div style={{ flex:1 }}>
          <div style={{ fontSize:15, fontWeight:600, color:V2_C.onSurface,
                        lineHeight:'20px', marginBottom:4 }}>
            {session.title}
          </div>
          <V2Row style={{ gap:6, flexWrap:'wrap' }}>
            <V2ModeBadge modeId={session.mode} />
            {session.location && (
              <V2Row style={{ gap:3 }}>
                <V2Ico name="place" size={11} color={V2_C.muted} />
                <span style={{ fontSize:11, color:V2_C.muted }}>{session.location}</span>
              </V2Row>
            )}
          </V2Row>
        </div>
        <div style={{ display:'flex', flexDirection:'column', alignItems:'flex-end', gap:4, flexShrink:0 }}>
          <span style={{ fontSize:11, color:V2_C.muted }}>{session.time}</span>
          <span style={{ fontSize:11, color:V2_C.muted }}>{session.dur}</span>
        </div>
      </V2Row>

      {/* Compact waveform */}
      <V2WaveSpeaker h={28} position={0.0} />

      {/* Row 2: speakers + tasks + tags */}
      <V2Row style={{ gap:8, flexWrap:'wrap' }}>
        {session.speakers > 1 && (
          <V2Row style={{ gap:3 }}>
            <V2Ico name="record_voice_over" size={12} color={V2_C.muted} />
            <span style={{ fontSize:11, color:V2_C.muted }}>{session.speakers} speakers</span>
          </V2Row>
        )}
        {session.tasks > 0 && (
          <div style={{ background:'#0A2018', borderRadius:9999, padding:'2px 8px',
                        display:'flex', alignItems:'center', gap:4 }}>
            <V2Ico name="checklist" size={11} color={V2_C.secondary} />
            <span style={{ fontSize:11, color:V2_C.secondary, fontWeight:500 }}>
              {session.tasks} task{session.tasks!==1?'s':''}
            </span>
          </div>
        )}
        {session.tags.slice(0,3).map(t=>(
          <div key={t} style={{ background:V2_C.surfVar, borderRadius:9999, padding:'2px 8px' }}>
            <span style={{ fontSize:11, color:V2_C.onSurfVar }}>#{t}</span>
          </div>
        ))}
      </V2Row>

      {session.preview && (
        <div style={{ fontSize:12, color:V2_C.onSurfVar, lineHeight:'17px', overflow:'hidden',
                      display:'-webkit-box', WebkitLineClamp:2, WebkitBoxOrient:'vertical' }}>
          {session.preview}
        </div>
      )}
    </V2Card>
  );
}

function HomeScreen({ onNavigate, onOpenSession, onRecord }) {
  const [search, setSearch] = React.useState('');
  const [searchOpen, setSearchOpen] = React.useState(false);
  const [filterMode, setFilterMode] = React.useState(null);

  const filtered = SESSIONS.filter(s=>{
    if(filterMode && s.mode!==filterMode) return false;
    if(search && !s.title.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  const visibleModes = MODES.filter(m=>m.id!=='new').slice(0,5);

  return (
    <div style={{ background:V2_C.bg, height:'100%', display:'flex', flexDirection:'column',
                  fontFamily:'DM Sans, sans-serif', position:'relative' }}>

      {/* Top bar */}
      <div style={{ display:'flex', alignItems:'center', minHeight:52,
                    padding:'0 8px 0 16px', flexShrink:0, gap:4 }}>
        <div style={{ flex:1, fontSize:17, fontWeight:700, color:V2_C.onSurface,
                      letterSpacing:'-0.3px' }}>Scrybe</div>
        <div onClick={()=>setSearchOpen(v=>!v)}
          style={{ padding:8, cursor:'pointer', display:'flex' }}>
          <V2Ico name="search" color={searchOpen?V2_C.primary:V2_C.onSurfVar} />
        </div>
        <div style={{ padding:8, cursor:'pointer', display:'flex' }}>
          <V2Ico name="tune" />
        </div>
      </div>

      <div style={{ flex:1, overflowY:'auto', display:'flex',
                    flexDirection:'column', gap:0, paddingBottom:0 }}>

        {/* Search bar */}
        {searchOpen && (
          <div style={{ margin:'0 14px 8px', background:V2_C.surfVar, borderRadius:12,
                        display:'flex', alignItems:'center', padding:'7px 10px', gap:7 }}>
            <V2Ico name="search" size={18} color={V2_C.muted} />
            <input value={search} onChange={e=>setSearch(e.target.value)}
              placeholder="Title, tag, location, speaker…"
              autoFocus
              style={{ flex:1, background:'none', border:'none', outline:'none', fontSize:14,
                       color:V2_C.onSurface, fontFamily:'DM Sans, sans-serif' }} />
            {search && (
              <div onClick={()=>setSearch('')} style={{ cursor:'pointer', display:'flex' }}>
                <V2Ico name="close" size={16} />
              </div>
            )}
          </div>
        )}

        {/* AI nudge */}
        <div style={{ margin:'6px 14px 10px',
                      background:'linear-gradient(135deg,#091D31,#0A1220)',
                      border:`1px solid ${V2_C.primary}25`,
                      borderRadius:14, padding:'10px 13px',
                      display:'flex', alignItems:'center', gap:10 }}>
          <V2Ico name="auto_awesome" size={16} color={V2_C.primary} />
          <div style={{ flex:1 }}>
            <span style={{ fontSize:13, color:V2_C.primary }}>
              3 open tasks across 2 sessions
            </span>
            <span style={{ fontSize:12, color:V2_C.muted }}> · tap to review</span>
          </div>
          <V2Ico name="chevron_right" size={18} color={V2_C.muted} />
        </div>

        {/* Mode filter chips */}
        <div style={{ position:'relative', marginBottom:12 }}>
          <div style={{ display:'flex', gap:8, padding:'0 14px', overflowX:'auto',
                        scrollbarWidth:'none', paddingBottom:4 }}>
            {/* All */}
            <div onClick={()=>setFilterMode(null)}
              style={{ background: filterMode===null ? V2_C.primary : V2_C.surfHigh,
                       borderRadius:9999, padding:'7px 16px', flexShrink:0, cursor:'pointer',
                       border:`1.5px solid ${filterMode===null ? V2_C.primary : V2_C.border}` }}>
              <span style={{ fontSize:13, fontWeight:600,
                             color:filterMode===null ? V2_C.primCont : V2_C.onSurfVar }}>
                All
              </span>
            </div>
            {/* Mode chips */}
            {visibleModes.map(m=>{
              const active = filterMode===m.id;
              return (
                <div key={m.id} onClick={()=>setFilterMode(active?null:m.id)}
                  style={{ background: active ? m.bg : V2_C.surfHigh,
                           borderRadius:9999, padding:'7px 14px',
                           display:'flex', alignItems:'center', gap:6,
                           flexShrink:0, cursor:'pointer',
                           border:`1.5px solid ${active ? m.color+'55' : V2_C.border}` }}>
                  <V2Ico name={m.icon} size={15} color={active ? m.color : V2_C.onSurfVar} />
                  <span style={{ fontSize:13, fontWeight: active ? 600 : 400,
                                 color: active ? m.color : V2_C.onSurface }}>
                    {m.label}
                  </span>
                </div>
              );
            })}
          </div>
          {/* Right fade hint */}
          <div style={{ position:'absolute', right:0, top:0, bottom:4, width:32, pointerEvents:'none',
                        background:'linear-gradient(to right, transparent, #09111A)' }} />
        </div>

        {/* Date header */}
        <div style={{ padding:'0 16px 6px',
                      fontSize:11, color:V2_C.muted, letterSpacing:'0.06em' }}>
          TODAY
        </div>

        {/* Session cards */}
        <div style={{ display:'flex', flexDirection:'column', gap:8, padding:'0 14px' }}>
          {filtered.length > 0
            ? filtered.map(s=>(
                <HomeSessionCard key={s.id} session={s} onOpen={onOpenSession} />
              ))
            : (
              <div style={{ fontSize:14, color:V2_C.muted, padding:'32px 0', textAlign:'center' }}>
                No recordings match
              </div>
            )
          }
          {/* FAB clearance — only pads after the last card */}
          <div style={{ height:76, flexShrink:0 }} />
        </div>
      </div>

      {/* FAB */}
      <div onClick={onRecord}
        style={{ position:'absolute', bottom:16, right:16, width:56, height:56,
                 borderRadius:9999, background:V2_C.primary, cursor:'pointer',
                 display:'flex', alignItems:'center', justifyContent:'center',
                 boxShadow:`0 4px 28px ${V2_C.primary}55`, zIndex:10 }}>
        <V2Ico name="mic" size={26} color={V2_C.primCont} />
      </div>

    </div>
  );
}

Object.assign(window, { HomeScreen });
