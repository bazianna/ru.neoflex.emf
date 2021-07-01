<template>
  <div class="nav">

    <section class="item">
      <span class="name">Имя:</span>
      <label class="radio">
        <input type="text" v-model="state.name"/>
        <input type="text" v-model="state.name"/>
        <input type="text" v-model="state.testState"/>
      </label>
    </section>

    <section class="item">
      <span class="name">Пол:</span>
      <label class="radio">
        <select>
          <option disabled value="">Выберите один из вариантов</option>
          <option>Жен</option>
          <option>Муж</option>
        </select>
      </label>
    </section>

    <section class="item">
      <span class="name">Дата и время рождения:</span>
        <input type="number" placeholder="день" />
        <input type="number" placeholder="месяц"/>
        <input type="number" placeholder="год"/>
        <input type="number" placeholder="час"/>
        <input type="number" placeholder="минуты"/>
    </section>

    <section class="item">
      <span class="name">Место рождения:</span>
      <input type="text" placeholder="место рождения" :value="state.name"/>
    </section>

    <section class="item">
      <span class="name">Время рождения не известно:</span>
      <input type="checkbox"/>
    </section>





    <input type="radio" name="type" value="D" v-model="state.type" />
    <input type="radio" name="type" value="C" v-model="state.type" />
    <input type="radio" name="type" value="B" v-model="state.type" />
    <input type="radio" name="type" value="E" v-model="state.type" />
  </div>
  <keep-alive>
    <component :is="view"></component>
  </keep-alive>
</template>

<script setup>
    import CardA from './cardA.vue'
    import CardB from './cardB.vue'
    import CardC from './cardC.vue'
    import CardD from './cardD.vue'
    import CardE from './cardE.vue'
    import { reactive, computed } from 'vue'

    const state = reactive({
      name: '',
      type: 'A',
      testState: '',
      testState1: ''
    })

    const view = computed(() => {
      return {
        A: CardA,
        B: CardB,
        C: CardC,
        D: CardD,
        E: CardE,
      }[state.type]
    })

    // const post = fetch("http://jsonplaceholder.typicode.com/posts", { "Content-Type": "application/json" })
    //     .then(response => response.json())
    //     .then(data => {
    //       state.testState = data[1].title
    //       console.log(data[1].title)
    //     })

    const get = fetch(`/bazi/natalChart?name="Аня"&minutes=${10}&hour=${5}&day=${25}&month=${3}&year=${2020}&placeOfBirth="Asia/Shanghai"`,
        {
          method: "GET",
          headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
          }
        })
        .then(response => response.json())
        .then(data => {
          state.testState1 = data
          console.log(data)
        })

</script>
<style scoped>
[type="radio"][value="A"]::before{
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M75 0c0 11.046-8.954 20-20 20S35 11.046 35 0H10C4.477 0 0 4.477 0 10v80c0 5.523 4.477 10 10 10h25c0-11.046 8.954-20 20-20s20 8.954 20 20h65c5.523 0 10-4.477 10-10V10c0-5.523-4.477-10-10-10H75z' fill='%23C4C4C4'/%3E%3C/svg%3E")
}
[type="radio"][value="D"]::before{
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M55 20c11.046 0 20-8.954 20-20h65c5.523 0 10 4.477 10 10v80c0 5.523-4.477 10-10 10H75c0-11.046-8.954-20-20-20s-20 8.954-20 20H10c-5.523 0-10-4.477-10-10V10C0 4.477 4.477 0 10 0h25c0 11.046 8.954 20 20 20zm0 40c5.523 0 10-4.477 10-10s-4.477-10-10-10-10 4.477-10 10 4.477 10 10 10z' fill='%23EC7979'/%3E%3C/svg%3E")
}
[type="radio"][value="B"]::before{
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M20 0h110c0 11.046 8.954 20 20 20v60c-11.046 0-20 8.954-20 20H20c0-11.046-8.954-20-20-20V20c11.046 0 20-8.954 20-20z' fill='%23C4C4C4'/%3E%3C/svg%3E")
}
[type="radio"][value="C"]::before{
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M65 0c0 11.046-8.954 20-20 20S25 11.046 25 0H10C4.477 0 0 4.477 0 10v80c0 5.523 4.477 10 10 10h15c0-11.046 8.954-20 20-20s20 8.954 20 20h20c0-11.046 8.954-20 20-20s20 8.954 20 20h15c5.523 0 10-4.477 10-10V10c0-5.523-4.477-10-10-10h-15c0 11.046-8.954 20-20 20S85 11.046 85 0H65z' fill='%23C4C4C4'/%3E%3C/svg%3E")
}
[type="radio"][value="E"]::before{
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M20 0c0 11.046-8.954 20-20 20v20c5.523 0 10 4.477 10 10S5.523 60 0 60v20c11.046 0 20 8.954 20 20h109c0-11.046 8.954-20 20-20 .335 0 .669.008 1 .025V60c-5.523 0-10-4.477-10-10s4.477-10 10-10V20c-11.046 0-20-8.954-20-20H20z' fill='%23EC7979'/%3E%3C/svg%3E")
}
</style>
