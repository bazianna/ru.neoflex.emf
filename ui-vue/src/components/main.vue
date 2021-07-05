<template>
  <div class="nav">

    <section class="item">
      <span class="name">Имя:</span>
      <label class="radio">
        <input type="text" v-model="state.name"/>
      </label>
    </section>

    <section class="item">
      <span class="name">Пол:</span>
      <label class="radio">
        <select v-model="state.sex">
          <option label="Жен" value="2"/>
          <option label="Муж" value="1"/>
        </select>
      </label>
    </section>

    <section class="item">
      <span class="name">Дата и время рождения:</span>
        <input type="number" placeholder="день" v-model="state.day"/>
        <input type="number" placeholder="месяц" v-model="state.month"/>
        <input type="number" placeholder="год" v-model="state.year"/>
        <input type="number" placeholder="час" v-model="state.hour"/>
        <input type="number" placeholder="минуты" v-model="state.minutes"/>
    </section>

    <section class="item">
      <span class="name">Место рождения:</span>
      <input type="text" placeholder="место рождения" v-model="state.placeOfBirth"/>
    </section>

    <section class="item">
      <span class="name">Время рождения не известно:</span>
      <input type="checkbox" v-model="state.hourNotKnown"/>
    </section>

    <section class="item">

      <button @click="getBaZiDate()">run</button>
    </section>
    <div style="display: inline-flex">
      <div >
        <div class="card-name" :style="style">Час</div>
        <div class="card-god" :style="style"></div>
        <div class="card-element" :style="style">
          <img class="icon-elements" :src="state.pathIconHourSky">
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart.hour.sky)}}</span>
        </div>
        <div class="card-element" :style="style" >
          <img class="icon-elements" :src="state.pathIconHourEarth">
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart.hour.earth)}}</span>
        </div>
      </div>

      <div>
        <div class="card-name" :style="style">День</div>
        <div class="card-god" :style="style"></div>
        <div class="card-element" :style="style">
          <img class="icon-elements" :src="state.pathIconDaySky">
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart.day.sky)}}</span>
        </div>
        <div class="card-element" :style="style" >
          <img class="icon-elements" :src="state.pathIconDayEarth">
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart.day.earth)}}</span>
        </div>
      </div>

      <div>
        <div class="card-name" :style="style">Месяц</div>
        <div class="card-god" :style="style"></div>
        <div class="card-element" :style="style">
          <img class="icon-elements" :src="state.pathIconMonthSky">
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart.month.sky)}}</span>
        </div>
        <div class="card-element" :style="style" >
          <img class="icon-elements" :src="state.pathIconMonthEarth">
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart.month.earth)}}</span>
        </div>
      </div>

      <div>
        <div class="card-name" :style="style">Год</div>
        <div class="card-god" :style="style"></div>
        <div class="card-element" :style="style">
          <img class="icon-elements" :src="state.pathIconYearSky">
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart.year.sky)}}</span>
        </div>
        <div class="card-element" :style="style" >
          <img class="icon-elements" :src="state.pathIconYearEarth">
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart.year.earth)}}</span>
        </div>
      </div>
    </div>


<!--    <input type="radio" name="type" value="D" v-model="state.type" />-->
<!--    <input type="radio" name="type" value="C" v-model="state.type" />-->
<!--    <input type="radio" name="type" value="B" v-model="state.type" />-->
<!--    <input type="radio" name="type" value="E" v-model="state.type" />-->
  </div>


<!--  <keep-alive>-->
<!--    <component :is="view"></component>-->
<!--  </keep-alive>-->
</template>

<script setup>
    import CardA from './cardA.vue'
    import CardB from './cardB.vue'
    import CardC from './cardC.vue'
    import CardD from './cardD.vue'
    import CardE from './cardE.vue'
    import { reactive, computed } from 'vue'

    const state = reactive({
      name: 'Анна',
      day: 5,
      month: 7,
      year: 2020,
      hour: 15,
      minutes: 30,
      placeOfBirth: 'Asia/Shanghai',
      sex: 2,
      hourNotKnown: false,
      natalChart: null,

      pathIconHourSky: null,
      pathIconHourEarth: null,
      pathIconDaySky: null,
      pathIconDayEarth: null,
      pathIconMonthSKy: null,
      pathIconMonthEarth: null,
      pathIconYearSky: null,
      pathIconYearEarth: null,

      type: 'A',
      testState: '',


    })

    const getElementName = (title) => {
      return {
        TreeYang: 'Дерево Ян',
        TreeYin: 'Дерево Инь',
        FireYang: 'Огонь Ян',
        FireYin: 'Огонь Инь',
        EarthYang: 'Земля Ян',
        EarthYin: 'Земля Инь',
        MetalYang: 'Металл Ян',
        MetalYin: 'Металл Инь',
        WaterYang: 'Вода Ян',
        WaterYin: 'Вода Инь',
        Rat: 'Вода Ян',
        Beef: 'Земля Инь',
        Tiger: 'Дерево Ян',
        Rabbit: 'Дерево Инь',
        Dragon: 'Земля Ян',
        Snake: 'Огонь Инь',
        Horse: 'Огонь Ян',
        Goat: 'Земля Инь',
        Monkey: 'Металл Ян',
        Chicken: 'Металл Инь',
        Dog: 'Земля Ян',
        Pig: 'Вода Инь'
      }[title]
    }

    const getBaZiDate = () => {
      state.pathIconHourSky = null
      state.pathIconHourEarth = null
      state.pathIconDaySky = null
      state.pathIconDayEarth = null
      state.pathIconMonthSky = null
      state.pathIconMonthEarth = null
      state.pathIconYearSky = null
      state.pathIconYearEarth = null
      state.natalChart = null
      fetch(`/bazi/natalChart?name=${state.name}&minutes=${state.minutes}&hour=${state.hour}&day=${state.day}&month=${state.month}&year=${state.year}&placeOfBirth=${state.placeOfBirth}&sex=${state.sex}&hourNotKnown=${state.hourNotKnown}`,
          {
            method: "GET",
            headers: {
              'Accept': 'application/json',
              'Content-Type': 'application/json'
            }
          })
          .then(response => response.json())
          .then(data => {
            const natalChart = data.contents.find((cont) => cont['eClass'] === 'ru.neoflex.emf.bazi.natalChart#//NatalChart' )
            state.natalChart = natalChart
            console.log(natalChart)
            state.pathIconHourSky = 'src/icons/' + natalChart.hour.sky.toString() + '.svg'
            state.pathIconHourEarth = 'src/icons/' + natalChart.hour.earth.toString() + '.svg'
            state.pathIconDaySky = 'src/icons/' + natalChart.day.sky.toString() + '.svg'
            state.pathIconDayEarth = 'src/icons/' + natalChart.day.earth.toString() + '.svg'
            state.pathIconMonthSky = 'src/icons/' + natalChart.month.sky.toString() + '.svg'
            state.pathIconMonthEarth = 'src/icons/' + natalChart.month.earth.toString() + '.svg'
            state.pathIconYearSky = 'src/icons/' + natalChart.year.sky.toString() + '.svg'
            state.pathIconYearEarth = 'src/icons/' + natalChart.year.earth.toString() + '.svg'
          })
    }

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
