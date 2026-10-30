<script setup lang="ts">
import { onMounted, ref } from 'vue';
import './login-screen.css';

const query = new URLSearchParams(window.location.search);
const signedOut = ref(query.get('signedOut') === '1');
const username = ref('');
const password = ref('');
const visible = ref(false);
const loading = ref(false);
const error = ref(loginErrorMessage(query.get('error')));
const status = ref('');

function loginErrorMessage(code: string | null) {
  switch (code) {
    case 'INVALID_CREDENTIALS': return '用户名或密码错误，请检查后重试。';
    case 'PROVIDER_UNAVAILABLE': return '企业登录服务暂时不可用，请稍后重试。';
    case 'FORBIDDEN': return '登录请求未通过安全校验，请重新打开登录页。';
    case 'INVALID_CAS_ASSERTION': return '登录信息已失效，请重新登录。';
    default: return '';
  }
}

function removeSignedOutMarker() {
  if (!signedOut.value && !query.has('error')) return;
  const url = new URL(window.location.href);
  url.searchParams.delete('signedOut');
  url.searchParams.delete('error');
  window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
}

async function redirectIfAlreadyAuthenticated() {
  try {
    const response = await fetch('/api/v1/session', {credentials: 'include', cache: 'no-store'});
    if (response.ok && !signedOut.value) window.location.replace('/meetings');
  } catch {
    // The login form remains usable when the session probe is unavailable.
  }
}

onMounted(() => {
  removeSignedOutMarker();
  void redirectIfAlreadyAuthenticated();
});

async function submit(event: Event) {
  event.preventDefault();
  if (loading.value) return;
  if (!username.value.trim() || !password.value.trim()) {
    error.value = '用户名或密码不能为空';
    return;
  }
  loading.value = true;
  error.value = '';
  status.value = '';
  // Native navigation preserves the CAS 303 redirect. Fetch with
  // redirect:"manual" turns that redirect into an opaque response in the
  // browser, which would hide the callback URL from the navigation stack.
  const form = document.createElement('form');
  form.method = 'post';
  form.action = '/auth/cas/login';
  form.style.display = 'none';
  for (const [name, value] of [['username', username.value.trim()], ['password', password.value]] as const) {
    const input = document.createElement('input');
    input.type = 'hidden'; input.name = name; input.value = value; form.append(input);
  }
  document.body.append(form);
  form.submit();
}
</script>

<template>
  <main class="login-screen">
    <section class="login-card" aria-labelledby="login-heading">
      <header class="login-brand">
        <img src="/cmf-login-logo.png" alt="招商基金" />
        <div><strong>招商基金</strong><small>CHINA MERCHANTS FUND</small></div>
        <span>承启招商<br />百年财富航程</span>
      </header>
      <div class="login-body">
        <h1 id="login-heading">登录 Omni</h1>
        <p v-if="signedOut" class="login-notice" role="status">已退出登录，请重新登录。</p>
        <p>使用企业账号登录，开始你的工作。</p>
        <form :aria-busy="loading" @submit="submit">
          <label for="login-username">用户</label>
          <input
            id="login-username"
            v-model="username"
            name="username"
            autocomplete="username"
            autofocus
            placeholder="请输入用户名"
            maxlength="200"
            :disabled="loading"
            required
          />
          <label for="login-password">密码</label>
          <div class="login-password">
            <input
              id="login-password"
              v-model="password"
              name="password"
              :type="visible ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="请输入密码"
              maxlength="1024"
              :disabled="loading"
              :aria-describedby="error ? 'login-error' : undefined"
              required
            />
            <button type="button" :aria-label="visible ? '隐藏密码' : '显示密码'" :aria-pressed="visible" @click="visible = !visible">
              {{ visible ? '隐藏' : '显示' }}
            </button>
          </div>
          <div v-if="error" id="login-error" class="login-error" role="alert">
            <span class="login-error-mark" aria-hidden="true">!</span>
            <span>{{ error }}</span>
          </div>
          <p v-if="status" role="status" aria-live="polite">{{ status }}</p>
          <button class="login-submit" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登录' }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>
