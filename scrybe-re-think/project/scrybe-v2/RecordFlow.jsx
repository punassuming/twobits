// scrybe-v2/RecordFlow.jsx
// ModeSheet → RecordingScreen (with live diarization + task extraction)

function ModeSheet({ onSelect, onDismiss }) {
  const [selected, setSelected] = React.useState('meeting');
  const m = MODES.find(x=>x.id===selected);

  return (
    <V2Sheet onDismiss={onDismiss} maxH={560}>

      {/* Header — pinned, never scrolls */}
      <div style={{ padding:'4px 16px 10px', flexShrink:0 }}>
        <div style={{ fontSize:16, fontWeight:600, color:V2_C.onSurface }}>
          What are you capturing?
        </div>
        <div style={{ fontSize:12, color:V2_C.muted, marginTop:2 }}>
          Mode shapes the live transcript and AI output
        </div>
      </div>

      {/* Mode grid — only this part scrolls */}
      <div style={{ flex:1, overflowY:'auto', padding:'0 16px', scrollbarWidth:'none' }}>
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:8, paddingBottom:4 }}>
          {MODES.map(mode=>(
            <div key={mode.id} onClick={()=>setSelected(mode.id)}
              style={{ background: selected===mode.id?mode.bg:V2_C.surfHigh,
                       borderRadius:14, padding:'11px 12px',
                       display:'flex', flexDirection:'column', gap:6, cursor:'pointer',
                       border:`1.5px solid ${selected===mode.id?mode.color+'55':V2_C.border}` }}>
              <V2Ico name={mode.icon} size={18} color={mode.color} />
              <div style={{ fontSize:13, fontWeight:600, color:V2_C.onSurface }}>{mode.label}</div>
              <div style={{ fontSize:11, color:V2_C.muted, lineHeight:'14px' }}>{mode.output}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Output preview — pinned above button, always visible */}
      {m && m.id!=='new' && (
        <div style={{ margin:'10px 16px 0', flexShrink:0,
                      background:m.bg, borderRadius:12, padding:'9px 12px',
                      border:`1px solid ${m.color}22`,
                      display:'flex', alignItems:'center', gap:8 }}>
          <V2Ico name="output" size={14} color={m.color} />
          <span style={{ fontSize:12, color:m.color }}>Will produce: {m.output}</span>
        </div>
      )}

      {/* CTA — always pinned at bottom */}
      <div style={{ padding:'10px 16px 24px', flexShrink:0,
                    borderTop:`1px solid ${V2_C.border}`, marginTop:10,
                    background:V2_C.surface }}>
        <button onClick={()=>onSelect(selected)}
          style={{ width:'100%', background:V2_C.primary, border:'none',
                   borderRadius:14, padding:'14px 0',
                   fontSize:15, fontWeight:600, color:V2_C.primCont,
                   cursor:'pointer', fontFamily:'DM Sans, sans-serif',
                   display:'flex', alignItems:'center', justifyContent:'center', gap:8 }}>
          <V2Ico name="mic" size={18} color={V2_C.primCont} />
          Start recording
        </button>
      </div>
    </V2Sheet>
  );
}

// ── Live recording screen ────────────────────────────────────

function LiveTranscriptLine({ line, index, visible }) {
  const sp = SPEAKERS[line.s]||SPEAKERS[0];
  return (
    <div style={{ display:'flex', gap:8, alignItems:'flex-start',
                  opacity: visible?1:0,
                  transition:`opacity 0.4s ease ${index*0.12}s` }}>
      <div style={{ width:6, height:6, borderRadius:'50%', background:sp.color,
                    marginTop:6, flexShrink:0 }} />
      <div>
        <span style={{ fontSize:10, color:sp.color, fontWeight:500, display:'block',
                       marginBottom:2 }}>{sp.label}</span>
        <span style={{ fontSize:13, color:V2_C.onSurface, lineHeight:'19px' }}>
          {line.text}
        </span>
      </div>
    </div>
  );
}

function TaskExtractionBanner({ task }) {
  const [show, setShow] = React.useState(false);
  React.useEffect(()=>{ setTimeout(()=>setShow(true),200); },[]);
  if(!show) return null;
  return (
    <div style={{ background:'#091812', border:`1px solid ${V2_C.secondary}33`,
                  borderRadius:10, padding:'7px 10px',
                  display:'flex', alignItems:'center', gap:7,
                  animation:'taskPop 0.3s ease' }}>
      <style>{`@keyframes taskPop{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:none}}`}</style>
      <V2Ico name="task_alt" size={14} color={V2_C.secondary} />
      <span style={{ fontSize:12, color:V2_C.secondary, flex:1 }}>Task extracted: {task}</span>
    </div>
  );
}

function RecordingScreen({ modeId, onStop, onCancel }) {
  const mode = MODES.find(m=>m.id===modeId)||MODES[0];
  const [elapsed, setElapsed] = React.useState(0);
  const [visibleLines, setVisibleLines] = React.useState(0);
  const [taskVisible, setTaskVisible] = React.useState(false);
  const [paused, setPaused] = React.useState(false);

  React.useEffect(()=>{
    const id = setInterval(()=>setElapsed(e=>e+1), 1000);
    return ()=>clearInterval(id);
  },[]);

  // Stream in transcript lines
  React.useEffect(()=>{
    if(visibleLines>=LIVE_LINES.length) return;
    const t = setTimeout(()=>setVisibleLines(v=>v+1), 1800);
    return ()=>clearTimeout(t);
  },[visibleLines]);

  // Show task banner after line 3
  React.useEffect(()=>{
    if(visibleLines>=3) setTaskVisible(true);
  },[visibleLines]);

  const fmt = s=>`${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;

  return (
    <div style={{ background:V2_C.bg, height:'100%', display:'flex',
                  flexDirection:'column', fontFamily:'DM Sans, sans-serif' }}>

      {/* Header */}
      <div style={{ display:'flex', alignItems:'center', minHeight:52,
                    padding:'0 8px', gap:8, flexShrink:0 }}>
        <div onClick={onCancel}
          style={{ padding:8, cursor:'pointer', display:'flex' }}>
          <V2Ico name="arrow_back" />
        </div>
        <V2ModeBadge modeId={modeId} size="md" />
        <div style={{ flex:1 }} />
        <div style={{ background:V2_C.surfVar, borderRadius:9999,
                      padding:'5px 12px', cursor:'pointer' }}
             onClick={()=>setPaused(p=>!p)}>
          <span style={{ fontSize:12, color:V2_C.onSurfVar }}>{paused?'Resume':'Pause'}</span>
        </div>
      </div>

      {/* Waveform + timer */}
      <div style={{ padding:'4px 14px 0', flexShrink:0 }}>
        <div style={{ background:V2_C.surface, borderRadius:16, padding:'12px 14px',
                      border:`1px solid ${V2_C.border}` }}>
          <V2WaveAnim h={52} />
          <V2Row style={{ justifyContent:'space-between', marginTop:8 }}>
            <V2Row style={{ gap:6 }}>
              <div style={{ width:7, height:7, borderRadius:'50%', background:V2_C.tertiary,
                            animation: paused?'none':'scrRec 1s ease-in-out infinite' }} />
              <span style={{ fontSize:12, color:V2_C.tertiary }}>
                {paused?'Paused':'Recording'}
              </span>
            </V2Row>
            <span style={{ fontSize:22, fontWeight:300, color:V2_C.tertiary,
                           fontVariantNumeric:'tabular-nums', letterSpacing:'-0.5px' }}>
              {fmt(elapsed)}
            </span>
          </V2Row>
        </div>
      </div>

      {/* Live transcript */}
      <div style={{ flex:1, overflowY:'auto', padding:'10px 14px',
                    display:'flex', flexDirection:'column', gap:2 }}>

        {/* Speaker legend */}
        {visibleLines>1 && (
          <V2Row style={{ gap:10, marginBottom:8, flexWrap:'wrap' }}>
            {SPEAKERS.map(sp=>(
              <V2Row key={sp.id} style={{ gap:4 }}>
                <div style={{ width:6, height:6, borderRadius:'50%', background:sp.color }} />
                <span style={{ fontSize:11, color:V2_C.muted }}>{sp.label}</span>
              </V2Row>
            ))}
          </V2Row>
        )}

        <div style={{ display:'flex', flexDirection:'column', gap:12 }}>
          {LIVE_LINES.slice(0,visibleLines).map((line,i)=>(
            <LiveTranscriptLine key={i} line={line} index={i} visible={true} />
          ))}
          {/* Cursor */}
          {visibleLines > 0 && !paused && (
            <div style={{ display:'flex', alignItems:'center', gap:8, paddingLeft:14 }}>
              <span style={{ display:'inline-block', width:2, height:14,
                             background:V2_C.primary, borderRadius:1,
                             animation:'scrBlink 1s step-end infinite' }} />
            </div>
          )}
        </div>

        {/* Task extraction banner */}
        {taskVisible && (
          <div style={{ marginTop:10 }}>
            <TaskExtractionBanner task="James to draft API schema by Thursday" />
          </div>
        )}
      </div>

      {/* Stop CTA */}
      <div style={{ padding:'8px 14px 20px', display:'flex', flexDirection:'column',
                    gap:8, flexShrink:0 }}>
        <button onClick={onStop}
          style={{ background:V2_C.primary, border:'none', borderRadius:14, padding:'14px 0',
                   fontSize:15, fontWeight:600, color:V2_C.primCont,
                   cursor:'pointer', fontFamily:'DM Sans, sans-serif' }}>
          Stop — process as {mode.label}
        </button>
        <button onClick={onStop}
          style={{ background:'transparent', border:`1px solid ${V2_C.surfVar}`,
                   borderRadius:14, padding:'11px 0', fontSize:13, color:V2_C.onSurfVar,
                   cursor:'pointer', fontFamily:'DM Sans, sans-serif' }}>
          Stop, save raw transcript only
        </button>
      </div>
    </div>
  );
}

Object.assign(window, { ModeSheet, RecordingScreen });
