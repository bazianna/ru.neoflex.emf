<template>
  <main>
    <div>
      <div class="card-up" :style="style"></div>
      <div class="card-down" :style="style" ></div>
    </div>

<!--    <aside class="side">-->
<!--      <section class="item">-->
<!--        <span class="name">radius</span>-->
<!--        <input type="range" v-model="state.radius" :data-tips="state.radius+'px'" :style="{'&#45;&#45;percent':state.radius / max.radius}" :max="max.radius"/>-->
<!--      </section>-->
<!--      <section class="item">-->
<!--        <span class="name">direction</span>-->
<!--        <label class="radio" data-tips="horizontal"><input type="radio" name="dir" value="horizontal" v-model="state.direction" /></label>-->
<!--        <label class="radio" data-tips="vertical"><input type="radio" name="dir" value="vertical" v-model="state.direction" /></label>-->
<!--      </section>-->
<!--      <section class="item" :direction="state.direction">-->
<!--        <span class="name">position</span>-->
<!--        <label class="radio" :data-tips="state.direction=='vertical'?'left':'top'"><input type="radio" name="pos" value="start" v-model="state.position"/></label>-->
<!--        <label class="radio" data-tips="center"><input type="radio" name="pos" value="center" v-model="state.position"/></label>-->
<!--        <label class="radio" :data-tips="state.direction=='vertical'?'right':'bottom'"><input type="radio" name="pos" value="end" v-model="state.position"/></label>-->
<!--      </section>-->
<!--      <section class="item" v-show="state.position!=='center'">-->
<!--        <span class="name">offset</span>-->
<!--        <input type="range" v-model="state.offset" :data-tips="state.offset+'px'" :style="{'&#45;&#45;percent':state.offset / max.offset}" :max="max.offset" />-->
<!--      </section>-->
<!--      <Pre :style="style"/>-->
<!--    </aside>-->
  </main>
</template>
<script setup>
import Pre from './pre.vue'
import { ref, reactive, computed, onMounted } from 'vue'

const props = {
  titleTest: Object,
  default: () => []
}

const state = reactive({ 
  // radius: 10,
  // direction: 'vertical',
  // position: 'start',
  // offset: 50,
  // width: 100,
  // height: 100
})

// const style = computed(() => {
//   const offset = state.position==='center'?'50%':state.offset+'px';
//   const position = `${state.direction==='horizontal'?'':'0 '}${state.position==='end'?'':'-'}${state.radius}px`;
//   return {
//     '-webkit-mask-image': `radial-gradient(circle at ${state.position==='end'?'right ':''}${state.direction==='horizontal'? state.radius+'px':offset} ${state.position==='end'?'bottom ':''}${state.direction==='horizontal'?offset:state.radius+'px'}, transparent ${state.radius}px, red ${state.radius}.5px)`,
//     '-webkit-mask-position': position,
//   }
// })

// const card = ref(null);

// onMounted(()=>{
//   const { width, height} = card.value.getBoundingClientRect();
//   state.width = width;
//   state.height = height;
// })

// const max = computed(() => {
//   return {
//     radius: Math.min(state.width, state.height) / 2,
//     offset: state.direction==='horizontal'? state.height / 2 : state.width / 2
//   }
// })

</script>
