// Common.jsx — Shared Scrybe UI Kit components
// Export to window so all screen components can use them

const SCRYBE_C = {
  bg:             '#0F1720',
  surface:        '#172431',
  surfaceHigh:    '#1C2B3B',
  surfaceVar:     '#243443',
  primary:        '#89C7FF',
  secondary:      '#88D7A8',
  tertiary:       '#FFB695',
  error:          '#FFB4AB',
  onSurface:      '#E2E8F0',
  onSurfaceVar:   '#8B9BAB',
  primaryCont:    '#003A63',
  onPrimaryCont:  '#C8E6FF',
  secondaryCont:  '#1B3D2F',
  tertiaryCont:   '#3B2515',
  errorCont:      '#4B1413',
};

const MOCK_SESSIONS = [
  { id: '1', title: 'Team standup — Thursday', time: 'Today · 9:14 AM', duration: '04:23', status: 'transcribed',
    preview: 'Blocked on API integration, need to sync with Dana before end of week. Also discussed Q3 roadmap.' },
  { id: '2', title: 'Product idea — notification redesign', time: 'Yesterday · 6:42 PM', duration: '02:11', status: 'recorded', preview: null },
  { id: '3', title: 'Weekly review', time: 'Mon · 8:00 PM', duration: '12:47', status: 'transcribed',
    preview: 'Good progress on the recording pipeline this week. Still need to address the dedup sync issue.' },
  { id: '4', title: 'Interview prep notes', time: 'Nov 12 · 3:30 PM', duration: '08:05', status: 'archived', preview: null },
  { id: '5', title: 'Brainstorm — app onboarding flow', time: 'Nov 10 · 11:23 AM', duration: '06:30', status: 'transcribed',
    preview: 'The first-time experience needs to be opinionated. We should default to dark mode and skip the setup wizard…' },
];

const STATUS_MAP = {
  transcribed:  { icon: 'check_circle',   bg: '#003A63', color: '#89C7FF' },
  recorded:     { icon: 'mic',            bg: '#1D2D3E', color: '#8B9BAB' },
  transcribing: { icon: 'hourglass_empty',bg: '#1B3D2F', color: '#88D7A8' },
  archived:     { icon: 'archive',        bg: '#3B2515', color: '#FFB695' },
  failed:       { icon: 'error',          bg: '#4B1413', color: '#FFB4AB' },
};

function BottomNav({ active, onNavigate }) {
  const items = [
    { id: 'capture',  icon: 'mic' },
    { id: 'history',  icon: 'history' },
    { id: 'profiles', icon: 'tune' },
    { id: 'settings', icon: 'settings' },
  ];
  return (
    <div style={{ background: SCRYBE_C.surface, borderTop: '1px solid rgba(255,255,255,0.06)',
                  display: 'flex', padding: '4px 0 8px', flexShrink: 0 }}>
      {items.map(item => (
        <div key={item.id} onClick={() => onNavigate(item.id)}
          style={{ flex: 1, display: 'flex', justifyContent: 'center', cursor: 'pointer', padding: '6px 0' }}>
          <div style={{ width: 64, height: 32, borderRadius: 9999,
                        background: active === item.id ? '#1C3A52' : 'transparent',
                        display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="material-icons-round"
              style={{ fontSize: 26, color: active === item.id ? SCRYBE_C.primary : SCRYBE_C.onSurfaceVar }}>
              {item.icon}
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}

function SectionCard({ children, style }) {
  return (
    <div style={{ background: SCRYBE_C.surfaceHigh, borderRadius: 24, padding: 16,
                  display: 'flex', flexDirection: 'column', gap: 10, ...style }}>
      {children}
    </div>
  );
}

function SectionHeader({ title, subtitle }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <div style={{ fontSize: 16, fontWeight: 600, color: SCRYBE_C.onSurface, lineHeight: '22px' }}>{title}</div>
      {subtitle && <div style={{ fontSize: 12, color: SCRYBE_C.onSurfaceVar, lineHeight: '16px' }}>{subtitle}</div>}
    </div>
  );
}

Object.assign(window, { SCRYBE_C, MOCK_SESSIONS, STATUS_MAP, BottomNav, SectionCard, SectionHeader });
