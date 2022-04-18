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
        <input type="number" placeholder="год" v-model="state.year" min="1930"/>
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
      <button @click="getDocx()">getWord</button>
    </section>
    <div style="display: inline-flex">

      <div v-for="pillarName in pillarNames" :key="pillarName">
        <div class="card-name" >{{ getPillarName(pillarName.pillar) }}</div>
        <div class="card-god">
          <span v-if="state.natalChart !== null">{{getGodName(state.natalChart[pillarName.pillar].god)}}</span>
        </div>
        <div class="card-element" >
          <img v-if="state.natalChart !== null" class="icon-elements" :src=getIconPath(state.natalChart[pillarName.pillar].sky)>
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart[pillarName.pillar].sky)}}</span>
        </div>
        <div class="card-element">
          <img v-if="state.natalChart !== null" class="icon-elements" :src=getIconPath(state.natalChart[pillarName.pillar].earth)>
          <span v-if="state.natalChart !== null">{{getElementName(state.natalChart[pillarName.pillar].earth)}}</span>
        </div>

        <div v-if="state.natalChart != null" class="card-qiPhase">
          <span>{{getElementName(state.natalChart[pillarName.pillar].qiPhaseFirstDegree)}} / {{getElementName(state.natalChart[pillarName.pillar].qiPhaseSecondDegree)}}</span>
        </div>

                                <div v-if="state.natalChart != null" class="card-hidden-element-first">
                                  <div v-if="state.natalChart[pillarName.pillar].Spirit !== undefined" v-for="spirit in state.natalChart[pillarName.pillar].Spirit" :key="spirit.name">
                                      {{spirit.description}}
                                  </div>
                                </div>

                  <div v-if="state.natalChart != null && state.natalChart[pillarName.pillar].hiddenPillar[0] !== undefined" class="card-hidden-element-first">
                    <span>{{getGodName(state.natalChart[pillarName.pillar].hiddenPillar[0].god)}}</span>
                    <img class="icon-hidden-elements" :src=getIconPath(state.natalChart[pillarName.pillar].hiddenPillar[0].sky)>
                    <span>{{getElementName(state.natalChart[pillarName.pillar].hiddenPillar[0].sky)}}</span>
                  </div>

                  <div v-if="state.natalChart != null && state.natalChart[pillarName.pillar].hiddenPillar[1] !== undefined" class="card-hidden-element">
                    <span>{{getGodName(state.natalChart[pillarName.pillar].hiddenPillar[1].god)}}</span>
                    <img class="icon-hidden-elements" :src=getIconPath(state.natalChart[pillarName.pillar].hiddenPillar[1].sky)>
                    <span>{{getElementName(state.natalChart[pillarName.pillar].hiddenPillar[1].sky)}}</span>
                  </div>

                  <div v-if="state.natalChart != null && state.natalChart[pillarName.pillar].hiddenPillar[2] !== undefined" class="card-hidden-element">
                    <span>{{getGodName(state.natalChart[pillarName.pillar].hiddenPillar[2].god)}}</span>
                    <img class="icon-hidden-elements" :src=getIconPath(state.natalChart[pillarName.pillar].hiddenPillar[2].sky)>
                    <span>{{getElementName(state.natalChart[pillarName.pillar].hiddenPillar[2].sky)}}</span>
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
    import Ecore from "ecore";

    const state = reactive({
      name: 'Анна',
      day: 5,
      month: 7,
      year: 1993,
      hour: 15,
      minutes: 30,
      placeOfBirth: 'Europe/Moscow',
      sex: 2,
      hourNotKnown: false,
      natalChart: null,

      type: 'A',
      testState: '',

      conclusions: null,

      natalChartId: 0
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
        Pig: 'Вода Инь',
        DiscoveringTheQi: '1',
        CirculatingTheQi: '2',
        GatheringTheQi: '3',
        AligningTheQi: '4',
        ProtectingTheQi: '5',
        PurifyingTheQi: '6',
        MobilizingTheQi: '7',
        DirectingTheQi: '8',
        ConsolidatingTheQi: '9',
        TransformingTheQi: '10',
        UnifyingTheQi: '11',
        TransmittingTheQi: '12'
      }[title]
    }

    const getPillarName = (pillar) => {
      return {
        hour: 'Час',
        day: 'День',
        month: 'Месяц',
        year: 'Год',
      }[pillar]
    }

    const pillarNames = [
      { pillar: 'hour' },
      { pillar: 'day' },
      { pillar: 'month' },
      { pillar: 'year' },
    ]

    const getGodName = (title) => {
      return {
        Friends: 'Друзья',
        RobWealth: 'Грабители богатства',
        EatingGod: 'Дух удовольствия',
        HurtingOfficer: 'Вызов власти',
        IndirectWealth: 'Непрямые деньги',
        DirectWealth: 'Прямые деньги',
        SevenKilling: '7-ой убийца',
        DirectOfficer: 'Прямая власть',
        IndirectResource: 'Непрямые ресурсы',
        DirectResource: 'Прямые ресурсы'
      }[title]
    }

    const getBaZiDate = () => {
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
            console.log(data)
            const natalChart = data.contents.find((cont) => cont['eClass'] === 'ru.neoflex.emf.bazi.natalChart#//NatalChart' )
            state.natalChart = natalChart
            state.conclusions = natalChart.conclusions
            state.natalChartId = natalChart._id
          })
    }

    const getDocx = () => {
      fetch(`/bazi/createWordPoi?id=${state.natalChartId}`,
          {
            method: "POST",
            headers: {
              'Accept': 'application/json',
              'Content-Type': 'application/json'
            }
          })
          .then(data => {
            console.log('getDocx OK')
          })
    }

    const getIconPath = (element) => {
      return 'src/icons/' + element.toString() + '.svg'
    }

</script>
<!--<style scoped>-->
<!--[type="radio"][value="A"]::before{-->
<!--  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M75 0c0 11.046-8.954 20-20 20S35 11.046 35 0H10C4.477 0 0 4.477 0 10v80c0 5.523 4.477 10 10 10h25c0-11.046 8.954-20 20-20s20 8.954 20 20h65c5.523 0 10-4.477 10-10V10c0-5.523-4.477-10-10-10H75z' fill='%23C4C4C4'/%3E%3C/svg%3E")-->
<!--}-->
<!--[type="radio"][value="D"]::before{-->
<!--  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M55 20c11.046 0 20-8.954 20-20h65c5.523 0 10 4.477 10 10v80c0 5.523-4.477 10-10 10H75c0-11.046-8.954-20-20-20s-20 8.954-20 20H10c-5.523 0-10-4.477-10-10V10C0 4.477 4.477 0 10 0h25c0 11.046 8.954 20 20 20zm0 40c5.523 0 10-4.477 10-10s-4.477-10-10-10-10 4.477-10 10 4.477 10 10 10z' fill='%23EC7979'/%3E%3C/svg%3E")-->
<!--}-->
<!--[type="radio"][value="B"]::before{-->
<!--  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M20 0h110c0 11.046 8.954 20 20 20v60c-11.046 0-20 8.954-20 20H20c0-11.046-8.954-20-20-20V20c11.046 0 20-8.954 20-20z' fill='%23C4C4C4'/%3E%3C/svg%3E")-->
<!--}-->
<!--[type="radio"][value="C"]::before{-->
<!--  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M65 0c0 11.046-8.954 20-20 20S25 11.046 25 0H10C4.477 0 0 4.477 0 10v80c0 5.523 4.477 10 10 10h15c0-11.046 8.954-20 20-20s20 8.954 20 20h20c0-11.046 8.954-20 20-20s20 8.954 20 20h15c5.523 0 10-4.477 10-10V10c0-5.523-4.477-10-10-10h-15c0 11.046-8.954 20-20 20S85 11.046 85 0H65z' fill='%23C4C4C4'/%3E%3C/svg%3E")-->
<!--}-->
<!--[type="radio"][value="E"]::before{-->
<!--  -webkit-mask-image: url("data:image/svg+xml,%3Csvg width='150' height='100' viewBox='0 0 150 100' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill-rule='evenodd' clip-rule='evenodd' d='M20 0c0 11.046-8.954 20-20 20v20c5.523 0 10 4.477 10 10S5.523 60 0 60v20c11.046 0 20 8.954 20 20h109c0-11.046 8.954-20 20-20 .335 0 .669.008 1 .025V60c-5.523 0-10-4.477-10-10s4.477-10 10-10V20c-11.046 0-20-8.954-20-20H20z' fill='%23EC7979'/%3E%3C/svg%3E")-->
<!--}-->
<!--</style>-->
