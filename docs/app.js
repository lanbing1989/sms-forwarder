function toggleFeatureDetail(card){const detail=card.querySelector('.feature-detail');const isHidden=detail.classList.contains('hidden');document.querySelectorAll('.feature-detail').forEach(d=>{d.classList.add('hidden');});if(isHidden){detail.classList.remove('hidden');detail.classList.add('slide-in');card.style.transform='translateY(-5px)';card.style.boxShadow='0 10px 30px rgba(91, 159, 239, 0.3)';}else{detail.classList.add('hidden');card.style.transform='translateY(0)';card.style.boxShadow='none';}}
function showScenario(type){const demoArea=document.getElementById('demo-area');const demoContent=document.getElementById('demo-content');demoArea.classList.remove('hidden');const scenarios={work:{title:'工作场景演示',messages:[{sender:'银行',content:'您的信用卡账单已生成，本月应还￥2,580.00',time:'09:15',important:true},{sender:'公司HR',content:'明天上午10点召开部门会议，请准时参加',time:'14:30',important:true},{sender:'快递',content:'您的快递已送达公司前台，请及时领取',time:'16:45',important:false}]},life:{title:'生活场景演示',messages:[{sender:'10086',content:'您的话费余额不足，请及时充值',time:'10:20',important:true},{sender:'物业',content:'本周六上午9点进行电梯维护，请提前安排',time:'11:00',important:false},{sender:'超市',content:'您积分已满2000分，可兑换精美礼品一份',time:'15:30',important:false}]},urgent:{title:'紧急场景演示',messages:[{sender:'医院',content:'您的体检报告已出，请尽快来院领取',time:'08:00',important:true},{sender:'学校',content:'孩子突发高烧，请立即来校接回',time:'13:15',important:true},{sender:'家人',content:'紧急情况，请速回电话',time:'19:45',important:true}]}};const scenario=scenarios[type];let content=`
        <h3 class="text-lg font-semibold text-gray-800 mb-4">${scenario.title}</h3>
        <div class="space-y-3">
    `;scenario.messages.forEach((msg,index)=>{const delay=index*300;setTimeout(()=>{addMessageAnimation(msg,index);},delay);const importantClass=msg.important?'border-l-4 border-red-500':'';const importantIcon=msg.important?'<span class="material-icons text-red-500 text-sm mr-2">priority_high</span>':'';content+=`
            <div class="message-item ${importantClass} bg-gray-50 rounded-lg p-3 opacity-0" id="message-${index}">
                <div class="flex items-start">
                    ${importantIcon}
                    <div class="flex-1">
                        <div class="flex items-center justify-between mb-1">
                            <span class="font-semibold text-gray-800">${msg.sender}</span>
                            <span class="text-xs text-gray-500">${msg.time}</span>
                        </div>
                        <p class="text-gray-600 text-sm">${msg.content}</p>
                        ${msg.important ? '<div class="mt-2"><span class="text-xs bg-red-100 text-red-600 px-2 py-1 rounded">已转发</span></div>' : ''}
                    </div>
                </div>
            </div>
        `;});content+=`
        </div>
        <div class="mt-4 text-center">
            <div class="inline-flex items-center text-green-600">
                <span class="material-icons mr-2">check_circle</span>
                <span class="text-sm">重要短信已自动转发到您的邮箱</span>
            </div>
        </div>
    `;demoContent.innerHTML=content;setTimeout(()=>{document.querySelectorAll('.message-item').forEach((item,index)=>{setTimeout(()=>{item.style.transition='opacity 0.5s ease-in-out';item.style.opacity='1';},index*100);});},100);}
function addMessageAnimation(msg,index){const messageElement=document.getElementById(`message-${index}`);if(messageElement){messageElement.classList.add('pulse-animation');if(msg.important){setTimeout(()=>{const forwardIcon=document.createElement('div');forwardIcon.innerHTML='<span class="material-icons text-blue-500">forward</span>';forwardIcon.className='absolute right-2 top-2 animate-pulse';messageElement.style.position='relative';messageElement.appendChild(forwardIcon);setTimeout(()=>{forwardIcon.remove();},2000);},500);}}}
function updateSensitivity(value){const valueDisplay=document.getElementById('sensitivity-value');valueDisplay.textContent=value+'%';const slider=document.querySelector('.slider');const percentage=value/100;slider.style.background=`linear-gradient(to right, #5B9FEF 0%, #5B9FEF ${percentage * 100}%, #E3F2FD ${percentage * 100}%, #E3F2FD 100%)`;updateSettingPreview();}
function updateTypeSelection(){const checkboxes=document.querySelectorAll('input[type="checkbox"]:checked');updateSettingPreview();}
function updateSettingPreview(){const preview=document.getElementById('setting-preview');const previewText=document.getElementById('preview-text');const sensitivity=document.getElementById('sensitivity-value').textContent;const checkboxes=document.querySelectorAll('input[type="checkbox"]:checked');if(checkboxes.length>0){preview.classList.remove('hidden');const types=[];checkboxes.forEach(checkbox=>{const label=checkbox.closest('label');const typeText=label.querySelector('p').textContent;types.push(typeText);});previewText.textContent=`关键词敏感度设置为${sensitivity}，转发类型：${types.join('、')}`;}else{preview.classList.add('hidden');}}
function handleDownload(){const modal=document.createElement('div');modal.className='fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50';modal.innerHTML=`
        <div class="bg-white rounded-2xl p-6 max-w-sm mx-4 slide-in">
            <div class="text-center">
                <div class="mb-4">
                    <span class="material-icons text-5xl text-green-500">check_circle</span>
                </div>
                <h3 class="text-xl font-semibold text-gray-800 mb-2">准备下载</h3>
                <p class="text-gray-600 mb-6">短信转发助手将开始下载，请稍候...</p>
                <div class="space-y-3">
                    <div class="bg-blue-50 rounded-lg p-3 text-left">
                        <div class="flex items-center mb-2">
                            <span class="material-icons text-blue-500 text-sm mr-2">info</span>
                            <span class="text-sm font-semibold text-gray-800">应用信息</span>
                        </div>
                        <div class="text-xs text-gray-600 space-y-1">
                            <p>• 版本：v2.1.0</p>
                            <p>• 大小：15.8MB</p>
                            <p>• 兼容：Android 6.0+</p>
                        </div>
                    </div>
                </div>
                <button onclick="closeDownloadModal()" class="mt-6 w-full bg-blue-500 text-white py-2 rounded-lg hover:bg-blue-600 transition-colors">
                    知道了
                </button>
            </div>
        </div>
    `;document.body.appendChild(modal);setTimeout(()=>{const modalContent=modal.querySelector('div > div');const progressDiv=document.createElement('div');progressDiv.className='mt-4';progressDiv.innerHTML=`
            <div class="w-full bg-gray-200 rounded-full h-2">
                <div class="bg-blue-500 h-2 rounded-full transition-all duration-3000" style="width: 0%" id="progress-bar"></div>
            </div>
            <p class="text-xs text-gray-500 mt-2">下载中... <span id="progress-text">0%</span></p>
        `;modalContent.appendChild(progressDiv);let progress=0;const progressBar=document.getElementById('progress-bar');const progressText=document.getElementById('progress-text');const interval=setInterval(()=>{progress+=Math.random()*15;if(progress>100)progress=100;progressBar.style.width=progress+'%';progressText.textContent=Math.floor(progress)+'%';if(progress>=100){clearInterval(interval);setTimeout(()=>{progressDiv.innerHTML=`
                        <div class="text-center text-green-600">
                            <span class="material-icons align-middle mr-1">check_circle</span>
                            <span class="text-sm">下载完成！</span>
                        </div>
                    `;},500);}},200);},2000);}
function closeDownloadModal(){const modal=document.querySelector('.fixed.inset-0');if(modal){modal.remove();}}
function showTerms(){const modal=document.createElement('div');modal.className='fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4';modal.innerHTML=`
        <div class="bg-white rounded-2xl p-6 max-w-md mx-4 max-h-[80vh] overflow-y-auto slide-in">
            <div class="text-center mb-6">
                <div class="mb-4">
                    <span class="material-icons text-5xl text-blue-500">description</span>
                </div>
                <h3 class="text-xl font-semibold text-gray-800 mb-2">服务条款</h3>
            </div>
            <div class="text-left text-gray-600 space-y-4 text-sm">
                <div>
                    <h4 class="font-semibold text-gray-800 mb-2">1. 服务说明</h4>
                    <p>短信转发助手为用户提供短信智能过滤和转发服务，帮助用户管理重要短信信息。</p>
                </div>
                <div>
                    <h4 class="font-semibold text-gray-800 mb-2">2. 用户责任</h4>
                    <p>用户应合法使用本服务，不得用于违法活动或侵犯他人权益。</p>
                </div>
                <div>
                    <h4 class="font-semibold text-gray-800 mb-2">3. 服务限制</h4>
                    <p>我们保留在必要时修改或终止服务的权利，并将提前通知用户。</p>
                </div>
                <div>
                    <h4 class="font-semibold text-gray-800 mb-2">4. 免责声明</h4>
                    <p>在法律允许范围内，我们对因使用本服务产生的损失不承担责任。</p>
                </div>
                <div>
                    <h4 class="font-semibold text-gray-800 mb-2">5. 争议解决</h4>
                    <p>如发生争议，双方应友好协商解决；协商不成的，提交有管辖权的人民法院处理。</p>
                </div>
            </div>
            <div class="mt-6 flex space-x-3">
                <button onclick="closeTermsModal()" class="flex-1 bg-gray-200 text-gray-800 py-2 rounded-lg hover:bg-gray-300 transition-colors">
                    关闭
                </button>
                <button onclick="closeTermsModal()" class="flex-1 bg-blue-500 text-white py-2 rounded-lg hover:bg-blue-600 transition-colors">
                    我已了解
                </button>
            </div>
        </div>
    `;document.body.appendChild(modal);modal.addEventListener('click',function(e){if(e.target===modal){closeTermsModal();}});}
function closeTermsModal(){const modal=document.querySelector('.fixed.inset-0');if(modal){modal.remove();}}
function showSupport(){const modal=document.createElement('div');modal.className='fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4';modal.innerHTML=`
        <div class="bg-white rounded-2xl p-6 max-w-md mx-4 slide-in">
            <div class="text-center mb-6">
                <div class="mb-4">
                    <span class="material-icons text-5xl text-blue-500">support_agent</span>
                </div>
                <h3 class="text-xl font-semibold text-gray-800 mb-2">客户支持</h3>
                <p class="text-gray-600">我们随时为您提供帮助</p>
            </div>
            <div class="space-y-4">
                <div class="bg-blue-50 rounded-lg p-4">
                    <h4 class="font-semibold text-gray-800 mb-3 flex items-center">
                        <span class="material-icons text-blue-500 mr-2">phone</span>
                        客服热线
                    </h4>
                    <p class="text-gray-700">180-0537-8833</p>
                    <p class="text-sm text-gray-500">工作时间：周一至周五 9:00-18:00</p>
                </div>
                
                <div class="bg-green-50 rounded-lg p-4">
                    <h4 class="font-semibold text-gray-800 mb-3 flex items-center">
                        <span class="material-icons text-green-500 mr-2">email</span>
                        邮件支持
                    </h4>
                    <p class="text-gray-700">lanbing89@gmail.com</p>
                    <p class="text-sm text-gray-500">24小时内回复</p>
                </div>
                
                <div class="bg-purple-50 rounded-lg p-4">
                    <h4 class="font-semibold text-gray-800 mb-3 flex items-center">
                        <span class="material-icons text-purple-500 mr-2">forum</span>
                        在线客服
                    </h4>
                    <p class="text-gray-700">应用内客服</p>
                    <p class="text-sm text-gray-500">实时在线支持</p>
                </div>
                
                <div class="bg-orange-50 rounded-lg p-4">
                    <h4 class="font-semibold text-gray-800 mb-3 flex items-center">
                        <span class="material-icons text-orange-500 mr-2">help</span>
                        常见问题
                    </h4>
                    <p class="text-gray-700">查看帮助文档</p>
                    <p class="text-sm text-gray-500">快速找到答案</p>
                </div>
            </div>
            
            <button onclick="closeSupportModal()" class="mt-6 w-full bg-blue-500 text-white py-2 rounded-lg hover:bg-blue-600 transition-colors">
                知道了
            </button>
        </div>
    `;document.body.appendChild(modal);modal.addEventListener('click',function(e){if(e.target===modal){closeSupportModal();}});}
function closeSupportModal(){const modal=document.querySelector('.fixed.inset-0');if(modal){modal.remove();}}
document.addEventListener('DOMContentLoaded',function(){const observerOptions={threshold:0.1,rootMargin:'0px 0px -50px 0px'};const observer=new IntersectionObserver((entries)=>{entries.forEach(entry=>{if(entry.isIntersecting){entry.target.classList.add('slide-in');observer.unobserve(entry.target);}});},observerOptions);document.querySelectorAll('.feature-card').forEach(card=>{observer.observe(card);});let ticking=false;function updateParallax(){const scrolled=window.pageYOffset;const parallaxElements=document.querySelectorAll('.bubble-animation');parallaxElements.forEach(element=>{const speed=0.5;element.style.transform=`translateY(${scrolled * speed}px)`;});ticking=false;}
function requestTick(){if(!ticking){window.requestAnimationFrame(updateParallax);ticking=true;}}
window.addEventListener('scroll',requestTick);updateSensitivity(50);});