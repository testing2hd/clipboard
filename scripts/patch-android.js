// Run after `npx cap add android`. Idempotent.
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const appId = require(path.join(root, 'capacitor.config.json')).appId;
const android = path.join(root, 'android');
if (!fs.existsSync(android)) throw new Error('android/ missing - run `npx cap add android` first');

// 1. Java sources (package line is rewritten to the appId)
const srcDir = path.join(root, 'native', 'java');
const dstDir = path.join(android, 'app/src/main/java', ...appId.split('.'));
fs.mkdirSync(dstDir, { recursive: true });
for (const f of fs.readdirSync(srcDir)) {
  const code = fs.readFileSync(path.join(srcDir, f), 'utf8').replace(/^package .*;/m, `package ${appId};`);
  fs.writeFileSync(path.join(dstDir, f), code);
}

// 2. Manifest
const mPath = path.join(android, 'app/src/main/AndroidManifest.xml');
let m = fs.readFileSync(mPath, 'utf8');
if (!m.includes('.PopupActivity')) {
  const perms = fs.readFileSync(path.join(root, 'native/manifest-permissions.xml'), 'utf8');
  const comps = fs.readFileSync(path.join(root, 'native/manifest-components.xml'), 'utf8');
  m = m.replace('<application', () => perms + '\n    <application');
  m = m.replace('</application>', () => comps + '\n    </application>');
  fs.writeFileSync(mPath, m);
}

// 3. Shizuku dependency + minSdk (Shizuku API needs 24+)
const gPath = path.join(android, 'app/build.gradle');
let g = fs.readFileSync(gPath, 'utf8');
if (!g.includes('dev.rikka.shizuku')) {
  g = g.replace(/dependencies\s*\{/, (x) =>
    x + '\n    implementation "dev.rikka.shizuku:api:13.1.5"\n    implementation "dev.rikka.shizuku:provider:13.1.5"');
  fs.writeFileSync(gPath, g);
}
const vPath = path.join(android, 'variables.gradle');
if (fs.existsSync(vPath)) {
  let v = fs.readFileSync(vPath, 'utf8');
  v = v.replace(/minSdkVersion\s*=\s*\d+/, 'minSdkVersion = 26');
  fs.writeFileSync(vPath, v);
}
console.log('Android project patched for', appId);
