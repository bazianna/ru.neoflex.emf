<template>

</template>
<script setup>
import Pre from './pre.vue'
import { ref, reactive, computed, onMounted } from 'vue'

const state = reactive({ 
  radius: 10,
  direction: 'vertical',
  position: 'start',
  offset: 50,
  width: 100,
  height: 100
})

const style = computed(() => {
  const offset = state.position==='center'?'50%':state.offset+'px';
  const position = `${state.direction==='horizontal'?'':'0 '}${state.position==='end'?'':'-'}${state.radius}px`;
  return {
    '-webkit-mask-image': `radial-gradient(circle at ${state.position==='end'?'right ':''}${state.direction==='horizontal'? state.radius+'px':offset} ${state.position==='end'?'bottom ':''}${state.direction==='horizontal'?offset:state.radius+'px'}, transparent ${state.radius}px, red ${state.radius}.5px)`,
    '-webkit-mask-position': position,
  }
})

const card = ref(null);

onMounted(()=>{
  const { width, height} = card.value.getBoundingClientRect();
  state.width = width;
  state.height = height;
})

const max = computed(() => {
  return {
    radius: Math.min(state.width, state.height) / 2,
    offset: state.direction==='horizontal'? state.height / 2 : state.width / 2
  }
})

</script>
