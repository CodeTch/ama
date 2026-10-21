import { defineConfig } from '@playwright/test';
process.env.NO_PROXY = process.env.no_proxy = 'localhost,127.0.0.1,127.0.0.2';
export default defineConfig({
 testDir: './tests', testMatch: /(?:visual|e2e)\/.*\.spec\.ts/, fullyParallel: false,
 forbidOnly: true, retries: 0, reporter: [['list'], ['junit', { outputFile: process.env.PLAYWRIGHT_JUNIT_OUTPUT_FILE || '../artifacts/web/playwright.xml' }]],
 outputDir: '../artifacts/web/test-results',
 snapshotPathTemplate: '{testDir}/visual/baselines/{projectName}/{arg}{ext}',
 expect: { toHaveScreenshot: { maxDiffPixels: 0, animations: 'disabled' } },
 use: { baseURL: 'http://127.0.0.2:4275', browserName: 'chromium', locale: 'zh-CN', timezoneId: 'Asia/Shanghai', colorScheme: 'light', deviceScaleFactor: 1, contextOptions: {reducedMotion: 'reduce'} },
 projects: [{name: '1440x900', use: {viewport: {width: 1440,height:900}}}, {name: '1024x768',use:{viewport:{width:1024,height:768}}}],
 webServer: { command: 'npm run dev -- --host 127.0.0.2 --port 4275 --strictPort', url: 'http://127.0.0.2:4275', reuseExistingServer: false }
});
