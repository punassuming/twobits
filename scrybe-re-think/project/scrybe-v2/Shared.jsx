// scrybe-v2/Shared.jsx — Tokens, data, and shared micro-components

const V2_C = {
  bg:        '#09111A',
  surface:   '#111C27',
  surfHigh:  '#172433',
  surfVar:   '#1D2E3F',
  border:    'rgba(255,255,255,0.07)',
  primary:   '#89C7FF',
  secondary: '#88D7A8',
  tertiary:  '#FFB695',
  amber:     '#FFD580',
  purple:    '#C4ABFF',
  pink:      '#FF9EC4',
  onSurface: '#E2E8F0',
  onSurfVar: '#8B9BAB',
  muted:     '#56697A',
  primCont:  '#003A63',
};

// Modes = tags + AI pipeline baked in. Users can extend.
const MODES = [
  { id:'meeting',      icon:'groups',        label:'Meeting',      color:V2_C.primary,   output:'Action items + summary',  bg:'#091D31' },
  { id:'idea',         icon:'lightbulb',     label:'Idea',         color:V2_C.amber,     output:'Brainstorm list',          bg:'#1A1400' },
  { id:'tasks',        icon:'checklist',     label:'Tasks',        color:V2_C.secondary, output:'Task list',                bg:'#091812' },
  { id:'conversation', icon:'forum',         label:'Conversation', color:V2_C.purple,    output:'Dialogue summary',         bg:'#130E22' },
  { id:'story',        icon:'auto_stories',  label:'Story',        color:V2_C.pink,      output:'Narrative write-up',       bg:'#1E0A12' },
  { id:'interview',    icon:'person_search', label:'Interview',    color:V2_C.tertiary,  output:'Q&A + highlights',         bg:'#1C0E00' },
  { id:'journal',      icon:'book',          label:'Journal',      color:V2_C.onSurfVar, output:'Plain transcript',         bg:'#111820' },
  { id:'new',          icon:'add_circle',    label:'New mode',     color:V2_C.muted,     output:'Custom pipeline',          bg:V2_C.surfVar },
];

// Speaker diarization colors
const SPEAKERS = [
  { id:0, label:'You',      color:'#89C7FF' },
  { id:1, label:'Dana K.',  color:'#C4ABFF' },
  { id:2, label:'James R.', color:'#88D7A8' },
];

// Live transcript lines (streamed in during recording)
const LIVE_LINES = [
  { s:0, text:'OK so the main blocker this week is the API integration timeline.' },
  { s:1, text:'Right — we need sign-off on the schema before we can proceed.' },
  { s:0, text:"I'm thinking we push dedup sync to next sprint and focus on core pipeline." },
  { s:2, text:'That works for me. I can have a draft schema ready by Thursday.' },
  { s:0, text:'Perfect. James drafts schema Thu, Dana reviews by Friday EOD.' },
];

// Extracted tasks (always required, not optional)
const EXTRACTED_TASKS = [
  { id:1, text:'James to draft API schema',        due:'Thursday',    assignee:'James R.', done:false },
  { id:2, text:'Dana to review schema by Friday',  due:'Friday',      assignee:'Dana K.',  done:false },
  { id:3, text:'Push dedup sync to next sprint',   due:null,          assignee:'You',      done:false },
];

const SESSIONS = [
  { id:'1', mode:'meeting',      title:'Q2 Planning call',       time:'Today · 2:32 PM',   dur:'14:32', location:'Figma HQ, SF',  speakers:3, tags:['q2','roadmap','api'],    tasks:3, preview:'API timeline and dedup sync discussed. Three blockers identified.' },
  { id:'2', mode:'tasks',        title:'Weekly review',          time:'Mon · 8:00 PM',     dur:'12:47', location:'Home',           speakers:1, tags:['weekly','review'],       tasks:5, preview:'Good progress on recording pipeline. Dedup sync still open.' },
  { id:'3', mode:'conversation', title:'Podcast — ep 14',        time:'Nov 12 · 3:30 PM',  dur:'38:05', location:'Studio B',       speakers:2, tags:['podcast','ai'],          tasks:0, preview:null },
  { id:'4', mode:'idea',         title:'Notification redesign',  time:'Nov 10 · 6:42 PM',  dur:'02:11', location:'Coffee shop',    speakers:1, tags:['ux','notifications'],    tasks:2, preview:'Smarter grouping — urgency classification as the key.' },
];

// ── Micro-components ──────────────────────────────────────────

function V2Ico({ name, size=20, color, style }) {
  return (
    <span className="material-icons-round"
      style={{ fontSize:size, color:color??V2_C.onSurfVar, lineHeight:1,
               flexShrink:0, display:'block', ...style }}>
      {name}
    </span>
  );
}

function V2Row({ children, style }) {
  return <div style={{ display:'flex', alignItems:'center', gap:8, ...style }}>{children}</div>;
}

function V2Card({ children, style, onClick }) {
  return (
    <div onClick={onClick}
      style={{ background:V2_C.surfHigh, borderRadius:18, padding:14,
               display:'flex', flexDirection:'column', gap:8,
               border:`1px solid ${V2_C.border}`, ...style }}>
      {children}
    </div>
  );
}

function V2ModeBadge({ modeId, size='sm' }) {
  const m = MODES.find(x=>x.id===modeId)||MODES[0];
  return (
    <div style={{ background:m.bg, borderRadius:9999,
                  padding: size==='sm'?'3px 7px':'5px 10px',
                  display:'flex', alignItems:'center', gap:4, flexShrink:0 }}>
      <V2Ico name={m.icon} size={size==='sm'?11:14} color={m.color} />
      <span style={{ fontSize:size==='sm'?11:12, color:m.color, fontWeight:500 }}>
        {m.label}
      </span>
    </div>
  );
}

function V2TopBar({ title, sub, right, back, onBack }) {
  return (
    <div style={{ display:'flex', alignItems:'center', minHeight:52,
                  padding:'0 8px', flexShrink:0, gap:2 }}>
      {back && (
        <div onClick={onBack} style={{ padding:8, cursor:'pointer', display:'flex' }}>
          <V2Ico name="arrow_back" />
        </div>
      )}
      <div style={{ flex:1, paddingLeft:back?2:10 }}>
        <div style={{ fontSize:17, fontWeight:600, color:V2_C.onSurface, lineHeight:'22px' }}>
          {title}
        </div>
        {sub && <div style={{ fontSize:11, color:V2_C.muted, marginTop:1 }}>{sub}</div>}
      </div>
      {right}
    </div>
  );
}

function V2Sheet({ children, onDismiss, maxH=460 }) {
  return (
    <div style={{ position:'absolute', inset:0, zIndex:100 }} onClick={onDismiss}>
      <div style={{ position:'absolute', inset:0, background:'rgba(0,0,0,0.62)' }} />
      <div onClick={e=>e.stopPropagation()}
        style={{ position:'absolute', bottom:0, left:0, right:0,
                 background:V2_C.surface, borderRadius:'22px 22px 0 0',
                 border:`1px solid ${V2_C.border}`, borderBottom:'none',
                 display:'flex', flexDirection:'column', maxHeight:maxH }}>
        <div style={{ display:'flex', justifyContent:'center', padding:'12px 0 4px', flexShrink:0 }}>
          <div style={{ width:36, height:4, borderRadius:2, background:V2_C.surfVar }} />
        </div>
        {children}
      </div>
    </div>
  );
}

// Animated waveform (recording)
function V2WaveAnim({ h=52 }) {
  const [bars, setBars] = React.useState(()=>
    Array.from({length:48},()=>0.1+Math.random()*0.4));
  React.useEffect(()=>{
    const id = setInterval(()=>
      setBars(p=>[...p.slice(1), 0.12+Math.random()*0.82]), 82);
    return ()=>clearInterval(id);
  },[]);
  return (
    <div style={{ height:h, display:'flex', alignItems:'center', gap:2, overflow:'hidden' }}>
      {bars.map((amp,i)=>{
        const recent = i>=bars.length-6;
        return (
          <div key={i} style={{ width:2.5, height:Math.max(amp*h,2), borderRadius:1.5,
                                background:recent?V2_C.tertiary:V2_C.primary,
                                opacity:recent?0.92:0.22+(i/bars.length)*0.52,
                                flexShrink:0, alignSelf:'center' }} />
        );
      })}
    </div>
  );
}

// Static waveform with speaker-colored regions (playback / detail)
function V2WaveSpeaker({ h=42, position=0.38 }) {
  const bars = React.useMemo(()=>
    Array.from({length:72},(_,i)=>
      0.1+Math.abs(Math.sin(i*0.38+1.1))*0.5+Math.abs(Math.sin(i*0.9))*0.3
    ),[]);
  // Rough speaker regions
  const getColor = i => {
    if(i<24) return SPEAKERS[0].color;
    if(i<46) return SPEAKERS[1].color;
    return SPEAKERS[2].color;
  };
  const pct = position;
  return (
    <div style={{ height:h, display:'flex', alignItems:'center', gap:1,
                  overflow:'hidden', position:'relative', cursor:'pointer' }}>
      {bars.map((amp,i)=>{
        const played = i/bars.length <= pct;
        return (
          <div key={i} style={{ flex:1, height:Math.max(amp*h,2), borderRadius:1,
                                alignSelf:'center',
                                background:played?getColor(i):'rgba(255,255,255,0.15)',
                                opacity:played?0.75:0.4 }} />
        );
      })}
      {/* Playhead */}
      <div style={{ position:'absolute', left:`${pct*100}%`, top:'5%',
                    width:2, height:'90%', background:'#fff',
                    transform:'translateX(-1px)', borderRadius:1, opacity:0.8 }} />
    </div>
  );
}

Object.assign(window, {
  V2_C, MODES, SPEAKERS, LIVE_LINES, EXTRACTED_TASKS, SESSIONS,
  V2Ico, V2Row, V2Card, V2ModeBadge, V2TopBar, V2Sheet,
  V2WaveAnim, V2WaveSpeaker,
});
