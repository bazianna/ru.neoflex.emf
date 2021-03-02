import Vue from 'vue'
import App from './App.vue'
// import store from '../store/store'
// import router from './router'

Vue.config.productionTip = false //так как сейчас находимся в разработке

new Vue({
  // router,
  // store,
  render: h => h(App),
}).$mount('#app')
