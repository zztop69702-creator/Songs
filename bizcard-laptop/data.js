// 원본 영업도우미 VER2.0에서 그대로 가져온 명함 색상·기본정보·소개글 30개
const THEMES = [
  { id: '1a', name: 'Navy & Gold', img: 'saturate(0.9) contrast(1.04)', banner: '#0E2238', bannerInk: '#FFFFFF', hi: '#D9B45F', body: '#F7F5F0', foot: '#F7F5F0', ink: '#13202F', sub: '#4E5968', phone: '#0E2238', rule: '#C9A24A', divider: '#D8D2C4', label: '#9A7A2E', blend: 'multiply', logo: 'brightness(1.12) contrast(1.25)' },
  { id: '1b', name: 'Graphite & Red', img: 'grayscale(1) contrast(1.06)', banner: '#201E1D', bannerInk: '#FFFFFF', hi: '#EC3013', body: '#F3F2F2', foot: '#F3F2F2', ink: '#201E1D', sub: '#55504D', phone: '#201E1D', rule: '#EC3013', divider: '#CFCCCA', label: '#B8230C', blend: 'multiply', logo: 'brightness(1.12) contrast(1.25)' },
  { id: '1c', name: 'Sage & Brass', img: 'sepia(0.12) saturate(0.85) contrast(1.03)', banner: '#24392F', bannerInk: '#F4F1E6', hi: '#E3C98A', body: '#F2F1EA', foot: '#E9E8DF', ink: '#1E2B24', sub: '#55625A', phone: '#24392F', rule: '#7C9583', divider: '#C9CCBE', label: '#2F5A47', blend: 'multiply', logo: 'brightness(1.12) contrast(1.25)' },
  { id: '1d', name: 'Midnight & Amber', img: 'brightness(0.93) contrast(1.08) saturate(0.8)', banner: '#0B0D10', bannerInk: '#FFFFFF', hi: '#F2B544', body: '#1A1D22', foot: '#14171B', ink: '#EDEEF0', sub: '#A3A9B2', phone: '#FFFFFF', rule: '#F2B544', divider: '#2E333A', label: '#F2B544', blend: 'screen', logo: 'brightness(1.12) contrast(1.25) invert(1) hue-rotate(180deg) contrast(1.2)' },
  { id: '1e', name: 'Steel & Ice', img: 'saturate(0.7) hue-rotate(-8deg) brightness(1.02)', banner: '#2F4A63', bannerInk: '#FFFFFF', hi: '#A8DAEE', body: '#EEF2F5', foot: '#E4EAEF', ink: '#1C2733', sub: '#4F5E6D', phone: '#2F4A63', rule: '#2F4A63', divider: '#C5CFD8', label: '#2F4A63', blend: 'multiply', logo: 'brightness(1.12) contrast(1.25)' }
];
const DEF = { title: '이사 / 덕트진단 1팀', name: '송유성', phone: '010-6214-7460', email: 'zztop73@hanmail.net', theme: '1a', cat: 'cabinet' };
const MSGS = {
  tab: [
    '환기는 감이 아니라 데이터입니다.\n\n공기의 흐름을 눈으로 확인하고, 성능을 수치로 검증합니다.\nTAB 전문가 {name}입니다.',
    '보이지 않는 공기도 측정하면 답이 보입니다.\n\n풍량·정압 측정으로 환기 문제의 원인을 정확히 찾아드립니다.\n(주)영일 {name}입니다.',
    '주방이 덥고 연기가 빠지지 않는다면, 원인은 덕트에 있습니다.\n\n기류가시화(Smoke Test)로 직접 보여드리겠습니다.\n{name} 드림',
    '학교 급식실의 공기가 아이들의 하루를 바꿉니다.\n\n급식실 환기 진단과 개선, 데이터로 증명하겠습니다.\nTAB 전문가 {name}입니다.',
    '측정하고, 개선하고, 다시 측정합니다.\n\n결과로 말하는 환기 전문가가 되겠습니다.\n(주)영일 {name}입니다.',
    '기류가시화(Smoke Test)로 공기의 길을 보여드립니다.\n\n어디서 새고 어디가 막혔는지 현장에서 바로 확인하실 수 있습니다.\nTAB 전문가 {name}입니다.',
    '경기도형 과업지시형 T.A.B, 기준에 맞게 정확히 수행합니다.\n\n측정부터 보고서까지 책임지고 마무리하겠습니다.\n(주)영일 {name}입니다.',
    '설계 풍량이 실제로 나오고 있는지 확인해 보셨나요?\n\n풍량·정압 측정으로 설비의 실제 성능을 확인해 드립니다.\n{name} {phone}',
    '공기질은 숫자로 관리해야 합니다.\n\n미세먼지·공기질 환경까지 함께 점검해 드리겠습니다.\nTAB 전문가 {name}입니다.',
    '오늘 측정 현장에서 뵙게 되어 반가웠습니다.\n\n측정 결과는 정리되는 대로 바로 보내드리겠습니다.\n(주)영일 {name}'
  ],
  general: [
    '좋은 인연으로 오래 기억되고 싶습니다.\n\n기계설비와 환기 문제로 도움이 필요할 때 떠오르는 사람, {name}이 되겠습니다.',
    '1979년부터 이어온 현장 경험으로 답하겠습니다.\n\n기계설비·가스·전기·창호까지, 필요하실 때 편하게 연락 주세요.\n{name} {phone}',
    '명함 한 장으로 시작된 인연, 오래 이어가고 싶습니다.\n\n언제든 필요하실 때 연락 주세요.\n{name} {phone}',
    '오늘 귀한 시간 내주셔서 감사합니다.\n\n말씀 나눈 내용은 꼼꼼히 정리해 다시 연락드리겠습니다.\n(주)영일 {name}',
    '안녕하세요, (주)영일 {title} {name}입니다.\n\n명함 보내드립니다. 저장해 두시고 필요하실 때 연락 주세요.',
    '얼굴과 이름을 함께 기억해 주시면 좋겠습니다.\n\n기계설비·환기 일이라면 언제든 먼저 찾아뵙겠습니다.\n{name} {phone}',
    '기계설비·가스·전기·금속구조 및 창호까지 직접 시공합니다.\n\n현장에 필요한 일을 한 번에 상담해 드리겠습니다.\n(주)영일 {name}',
    '연락 주셔서 감사합니다.\n\n확인 후 빠르게 회신드리겠습니다.\n(주)영일 {name}',
    '작은 문의도 반갑게 듣겠습니다.\n\n현장에서 답을 찾는 사람, {name}입니다.\n{phone} / {email}',
    '오늘 나눈 이야기 잊지 않겠습니다.\n\n다음에 뵐 때는 더 좋은 제안으로 찾아뵙겠습니다.\n(주)영일 {name} 드림'
  ],
  cabinet: [
    '소음은 줄이고, 필요한 풍량은 확보해야 합니다.\n\n환기성능과 소음저감을 함께 고려하는 소음캐비닛 전문가 {name}입니다.',
    '근접건물 소음 민원, 설비를 멈추지 않고 해결할 수 있습니다.\n\n소음캐비닛으로 소음은 막고 환기 성능은 지키겠습니다.\n(주)영일 {name}입니다.',
    '실외기와 배기팬 소음, 현장마다 원인이 다릅니다.\n\n소음 측정 후 현장에 맞는 소음캐비닛을 설계해 드립니다.\n{name} {phone}',
    '소음을 막으면 풍량이 줄어드는 문제, 함께 풀어드리겠습니다.\n\n차음과 환기를 동시에 검증하는 소음캐비닛 전문가 {name}입니다.',
    '이웃과의 소음 갈등, 설비로 해결하는 방법이 있습니다.\n\n현장 확인 후 정확한 견적을 드리겠습니다.\n(주)영일 {name}',
    '오늘 현장 확인 감사했습니다.\n\n소음캐비닛 설치 방안과 견적은 정리해서 보내드리겠습니다.\n(주)영일 {name}',
    '설치 전과 후의 소음을 수치로 비교해 드립니다.\n\n결과로 증명하는 소음캐비닛 시공, {name}이 책임지겠습니다.',
    '주방 배기와 옥상 설비 소음, 한 번에 점검해 드립니다.\n\n환기와 소음을 함께 보는 전문가에게 맡겨주세요.\n{name} {phone}',
    '소음 민원은 빨리 대응할수록 비용이 줄어듭니다.\n\n필요하실 때 바로 연락 주시면 현장으로 찾아가겠습니다.\n(주)영일 {name}입니다.',
    '조용한 설비가 좋은 이웃을 만듭니다.\n\n소음캐비닛 상담이 필요하시면 언제든 연락 주세요.\n{name} {phone}'
  ]
};
const CATS = { tab: 'TAB·기류가시화', cabinet: '소음캐비닛', general: '얼굴명함' };
