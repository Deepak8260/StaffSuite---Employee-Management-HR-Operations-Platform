/** Simple office-towers illustration for the home banner. Decorative only. */
export default function BuildingArt({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 300 170" role="presentation" aria-hidden="true" preserveAspectRatio="xMaxYMax meet">
      <defs>
        <linearGradient id="glassBack" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#bcd6f2" />
          <stop offset="1" stopColor="#7fa9d6" />
        </linearGradient>
        <linearGradient id="glassFront" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#8fb9e4" />
          <stop offset="1" stopColor="#3f73b5" />
        </linearGradient>
        <pattern id="windows" width="11" height="11" patternUnits="userSpaceOnUse">
          <rect width="11" height="1.4" fill="#ffffff" opacity="0.55" />
          <rect width="1.4" height="11" fill="#ffffff" opacity="0.45" />
        </pattern>
      </defs>
      <rect x="150" y="14" width="66" height="156" fill="url(#glassBack)" />
      <rect x="150" y="14" width="66" height="156" fill="url(#windows)" />
      <rect x="92" y="52" width="92" height="118" fill="url(#glassFront)" />
      <rect x="92" y="52" width="92" height="118" fill="url(#windows)" />
      <rect x="92" y="52" width="92" height="5" fill="#2f5f9c" />
      <rect x="196" y="86" width="74" height="84" fill="url(#glassBack)" />
      <rect x="196" y="86" width="74" height="84" fill="url(#windows)" />
      <rect x="196" y="86" width="74" height="4" fill="#5d8fc4" />
      <rect x="0" y="166" width="300" height="4" fill="#cfe0f3" />
      <g fill="#3f8f5e">
        <circle cx="262" cy="150" r="17" />
        <circle cx="282" cy="156" r="12" />
        <circle cx="76" cy="152" r="14" />
      </g>
      <g fill="#6b4f3a">
        <rect x="260" y="160" width="4" height="10" />
        <rect x="74" y="162" width="4" height="8" />
      </g>
    </svg>
  )
}
