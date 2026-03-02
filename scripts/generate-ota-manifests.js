#!/usr/bin/env node
/* eslint-disable no-console */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const [
  ,
  ,
  buildDirArg = '/home/shrawan/Auramusic/tmp/ota-build',
  appJsonArg = '/home/shrawan/Auramusic/AuramusicExpo/app.json',
  baseUrlArg = 'https://teamshryne.github.io/mediyo-ota',
] = process.argv;

const buildDir = path.resolve(buildDirArg);
const appJsonPath = path.resolve(appJsonArg);
const baseUrl = baseUrlArg.replace(/\/+$/, '');

const metadataPath = path.join(buildDir, 'metadata.json');
if (!fs.existsSync(metadataPath)) {
  throw new Error(`metadata.json not found at: ${metadataPath}`);
}
if (!fs.existsSync(appJsonPath)) {
  throw new Error(`app.json not found at: ${appJsonPath}`);
}

const metadata = JSON.parse(fs.readFileSync(metadataPath, 'utf8'));
const app = JSON.parse(fs.readFileSync(appJsonPath, 'utf8'));

const runtimeVersion = app?.expo?.version;
if (!runtimeVersion) {
  throw new Error('expo.version missing in app.json');
}

const androidMeta = metadata?.fileMetadata?.android;
if (!androidMeta?.bundle) {
  throw new Error('Android bundle not found in metadata.json');
}

const mime = (ext) =>
  (
    {
      png: 'image/png',
      jpg: 'image/jpeg',
      jpeg: 'image/jpeg',
      ttf: 'font/ttf',
      otf: 'font/otf',
      hbc: 'application/x-hermes-bytecode',
      js: 'application/javascript',
    }[ext] || 'application/octet-stream'
  );

const seen = new Set();
const assets = [];
for (const item of androidMeta.assets || []) {
  const key = `${item.path}.${item.ext}`;
  if (seen.has(key)) continue;
  seen.add(key);
  assets.push({
    key: path.basename(item.path),
    fileExtension: item.ext,
    url: `${baseUrl}/${item.path}`,
    contentType: mime(item.ext),
  });
}

const launchExt = path.extname(androidMeta.bundle).replace('.', '') || 'js';
const launchKey = path.basename(androidMeta.bundle).split('.')[0];

const manifest = {
  id: crypto.randomUUID(),
  createdAt: new Date().toISOString(),
  runtimeVersion,
  launchAsset: {
    key: launchKey,
    url: `${baseUrl}/${androidMeta.bundle}`,
    contentType: mime(launchExt),
    fileExtension: launchExt,
  },
  assets,
  metadata: {
    platform: 'android',
    channel: 'production',
  },
  extra: {
    expoClient: {
      name: app?.expo?.name || 'Mediyo',
      slug: app?.expo?.slug || 'auramusic',
      version: runtimeVersion,
    },
  },
};

const manifestResponse = { manifest };
const noUpdate = {
  directive: 'noUpdateAvailable',
  reason: 'No compatible update found',
};

fs.writeFileSync(path.join(buildDir, 'manifest.android.json'), `${JSON.stringify(manifest, null, 2)}\n`);
fs.writeFileSync(
  path.join(buildDir, 'manifest-response.android.json'),
  `${JSON.stringify(manifestResponse, null, 2)}\n`
);
fs.writeFileSync(path.join(buildDir, 'no-update-response.json'), `${JSON.stringify(noUpdate, null, 2)}\n`);

console.log(`Generated OTA manifests in: ${buildDir}`);
