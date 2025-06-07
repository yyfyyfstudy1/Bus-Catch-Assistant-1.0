<script setup>
import TransportInfo from './components/TransportInfo.vue'
</script>

<template>
  <div class="app">
    <header>
      <h1>阿凡哥赶车助手</h1>
    </header>
    <main>
      <TransportInfo />
    </main>
  </div>
</template>

<style>
.app {
  font-family: Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  margin: 0;
  padding: 0;
}

header {
  background-color: #4CAF50;
  color: white;
  padding: 1rem;
  text-align: center;
}

main {
  padding: 20px;
}

body {
  margin: 0;
  min-height: 100vh;
  background-color: #f0f2f5;
}
</style>
