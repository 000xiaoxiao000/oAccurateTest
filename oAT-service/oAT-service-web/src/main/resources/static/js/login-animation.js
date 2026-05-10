const canvas = document.getElementById('login-canvas');
const ctx = canvas.getContext('2d');

let width, height;
let characters = [];
const characterCount = 12; // 减少数量，大尺寸更舒适
let mouseX = 0;
let mouseY = 0;
let isError = false;

// 扩展颜色方案 - 添加更多活泼的颜色
const colors = [
    '#00b5ad', // 青色
    '#fbbd08', // 黄色
    '#f2711c', // 橙色
    '#db2828', // 红色
    '#2185d0', // 蓝色
    '#FF9A8A', // 玫瑰粉色
    '#b5cc18', // 绿色
    '#a333c8', // 紫色
    '#00b5cc', // 天蓝色
    '#21ba45', // 翠绿色
    '#f2c037', // 金黄色
    '#e07b53'  // 珊瑚色
];

function init() {
    resize();
    for (let i = 0; i < characterCount; i++) {
        characters.push(new Character());
    }
    animate();
}

function resize() {
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
}

window.addEventListener('resize', resize);
window.addEventListener('mousemove', (e) => {
    mouseX = e.clientX;
    mouseY = e.clientY;
});

class Character {
    constructor() {
        this.x = Math.random() * width;
        this.y = Math.random() * height;
        this.radius = 30 + Math.random() * 20; // 显著放大基础半径
        this.color = colors[Math.floor(Math.random() * colors.length)];
        this.angle = 0;
        this.type = Math.floor(Math.random() * 3);
        this.floatOffset = Math.random() * Math.PI * 2;
        // 瞳孔平滑偏移值（在局部坐标系中）
        this.pupilOffsetX = 0;
        this.pupilOffsetY = 0;
    }

    draw() {
        const dx = mouseX - this.x;
        const dy = mouseY - this.y;
        // 计算目标角度
        const targetAngle = Math.atan2(dy, dx);

        // 平滑旋转处理 (Smooth rotation)
        let angleDiff = targetAngle - this.angle;
        // 确保角度差在 -PI 到 PI 之间
        while (angleDiff > Math.PI) angleDiff -= Math.PI * 2;
        while (angleDiff < -Math.PI) angleDiff += Math.PI * 2;

        // 极低旋转跟随速度，产生极大的延迟感，使眼球实时追踪鼠标的动作更加显眼
        this.angle += angleDiff * 0.005;

        ctx.save();
        ctx.translate(this.x, this.y);

        const wobble = Math.sin(Date.now() / 500 + this.floatOffset) * 2;
        ctx.translate(0, wobble);

        // 把身体、装饰物、眼睛都放在同一个旋转变换中，让它们成为一体
        ctx.save();
        ctx.rotate(this.angle);

        // 基础形状（身体）
        ctx.beginPath();
        ctx.ellipse(0, 0, this.radius, this.radius * 0.9, 0, 0, Math.PI * 2);
        ctx.fillStyle = this.color;
        ctx.fill();

        // 装饰物（与身体一起旋转/移动）
        if (this.type === 1) { // 领结：在脖子位置
            ctx.fillStyle = '#333';
            const bowWidth = Math.max(10, this.radius * 0.35);
            const bowHeight = Math.max(6, this.radius * 0.2);
            const yOffset = this.radius * 0.9; // 更靠近身体下方，作为脖子/胸前装饰

            ctx.beginPath();
            // 左侧三角形
            ctx.moveTo(-bowWidth, yOffset - bowHeight/2);
            ctx.lineTo(0, yOffset);
            ctx.lineTo(-bowWidth, yOffset + bowHeight/2);
            ctx.fill();

            // 右侧三角形
            ctx.beginPath();
            ctx.moveTo(bowWidth, yOffset - bowHeight/2);
            ctx.lineTo(0, yOffset);
            ctx.lineTo(bowWidth, yOffset + bowHeight/2);
            ctx.fill();

            // 中间小圆点
            ctx.beginPath();
            ctx.arc(0, yOffset, Math.max(2, this.radius * 0.06), 0, Math.PI * 2);
            ctx.fill();
        } else if (this.type === 2) { // 小帽子：在头顶
            ctx.fillStyle = '#333';
            const hatY = -this.radius * 1.05; // 稍微更靠上
            // 帽檐和帽顶按比例扩大一些
            ctx.fillRect(-this.radius * 0.6, hatY - 4, this.radius * 1.2, 6); // 帽檐
            ctx.fillRect(-this.radius * 0.35, hatY - 16, this.radius * 0.7, 12); // 帽顶
        }

        // 眼睛（在同一变换下绘制，保证与身体与装饰物一致）
        ctx.fillStyle = 'white';
        const eyeOffsetX = this.radius * 0.35;
        const eyeOffsetY = -this.radius * 0.15; // 眼睛稍微靠上
        const eyeSize = this.radius * 0.25; // 稍微缩小，和身体比例更和谐

        // 计算鼠标在角色局部坐标系内的向量：world -> local (旋转 -angle)
        const cosA = Math.cos(this.angle);
        const sinA = Math.sin(this.angle);
        const lx = dx * cosA + dy * sinA; // local x
        const ly = -dx * sinA + dy * cosA; // local y

        const distLocal = Math.hypot(lx, ly);
        let localAngle = Math.atan2(ly, lx);
        // 限制眼睛最大转动角度，避免瞳孔跑出眼窝
        const maxEyeAngle = Math.PI / 4; // 45度
        if (localAngle > maxEyeAngle) localAngle = maxEyeAngle;
        if (localAngle < -maxEyeAngle) localAngle = -maxEyeAngle;

        const pupilMaxDist = eyeSize * 0.45;
        const targetPX = Math.cos(localAngle) * Math.min(distLocal, pupilMaxDist);
        const targetPY = Math.sin(localAngle) * Math.min(distLocal, pupilMaxDist);

        // 平滑追踪（X/Y）- 这里的数值调大，会让眼球转动比身体旋转更快、更灵敏
        const smooth = 0.25;
        this.pupilOffsetX += (targetPX - this.pupilOffsetX) * smooth;
        this.pupilOffsetY += (targetPY - this.pupilOffsetY) * smooth;

        // 画眼窝（左右对称）
        ctx.beginPath();
        ctx.arc(-eyeOffsetX, eyeOffsetY, eyeSize, 0, Math.PI * 2);
        ctx.arc(eyeOffsetX, eyeOffsetY, eyeSize, 0, Math.PI * 2);
        ctx.fill();

        // 瞳孔（根据局部偏移绘制）
        ctx.fillStyle = 'black';
        let pupilSize = eyeSize * 0.5;
        if (isError) pupilSize = eyeSize * 0.2;

        ctx.beginPath();
        ctx.arc(-eyeOffsetX + this.pupilOffsetX, eyeOffsetY + this.pupilOffsetY, pupilSize, 0, Math.PI * 2);
        ctx.arc(eyeOffsetX + this.pupilOffsetX, eyeOffsetY + this.pupilOffsetY, pupilSize, 0, Math.PI * 2);
        ctx.fill();

        // 表情（嘴）
        ctx.strokeStyle = 'rgba(0,0,0,0.4)';
        ctx.lineWidth = 2.5;
        ctx.beginPath();
        if (isError) {
            ctx.moveTo(-6, 8);
            ctx.bezierCurveTo(-3, 4, 3, 12, 6, 8);
            ctx.stroke();
        } else {
            ctx.arc(0, 4, 8, 0.2, Math.PI - 0.2);
            ctx.stroke();
        }

        ctx.restore();

        ctx.restore();
    }

    update() {
        this.x += Math.sin(Date.now() / 2000 + this.floatOffset) * 0.3;
        this.y += Math.cos(Date.now() / 2000 + this.floatOffset) * 0.3;

        if (this.x < -100) this.x = width + 100;
        if (this.x > width + 100) this.x = -100;
        if (this.y < -100) this.y = height + 100;
        if (this.y > height + 100) this.y = -100;
    }
}

function animate() {
    ctx.clearRect(0, 0, width, height);
    characters.forEach(c => {
        c.update();
        c.draw();
    });
    requestAnimationFrame(animate);
}

function triggerErrorExpression() {
    isError = true;
    setTimeout(() => {
        isError = false;
    }, 3000);
}

window.setLoginError = triggerErrorExpression;

// Logo Character Animation
function initLogo() {
    const logoCanvas = document.getElementById('logo-canvas');
    if (!logoCanvas) return;
    const lctx = logoCanvas.getContext('2d');
    const lWidth = logoCanvas.width;
    const lHeight = logoCanvas.height;

    // Create a special character for the logo
    const logoChar = new Character();
    logoChar.x = lWidth / 2;
    logoChar.y = lHeight / 2;
    logoChar.radius = 22; // 调整到适合 60x60 canvas 的尺寸
    logoChar.color = '#00b5ad'; // Match 'teal' theme

    function animateLogo() {
        lctx.clearRect(0, 0, lWidth, lHeight);

        // 获取鼠标相对于 logo 画布中心的偏移，用于计算眼睛转动
        const rect = logoCanvas.getBoundingClientRect();
        const lMouseX = mouseX - (rect.left + lWidth / 2);
        const lMouseY = mouseY - (rect.top + lHeight / 2);
        const lookAngle = Math.atan2(lMouseY, lMouseX);

        lctx.save();
        lctx.translate(logoChar.x, logoChar.y);

        const wobble = Math.sin(Date.now() / 500) * 2;
        lctx.translate(0, wobble);
        // 移除身体旋转: lctx.rotate(logoChar.angle);

        // Body (不随鼠标旋转)
        lctx.beginPath();
        lctx.ellipse(0, 0, logoChar.radius, logoChar.radius * 0.9, 0, 0, Math.PI * 2);
        lctx.fillStyle = logoChar.color;
        lctx.fill();

        // Eyes
        const eyeOffsetX = logoChar.radius * 0.35;
        const eyeOffsetY = -logoChar.radius * 0.2;
        const eyeSize = logoChar.radius * 0.3;

        // 左眼窝
        lctx.fillStyle = 'white';
        lctx.beginPath();
        lctx.arc(-eyeOffsetX, eyeOffsetY, eyeSize, 0, Math.PI * 2);
        lctx.fill();
        // 右眼窝
        lctx.beginPath();
        lctx.arc(eyeOffsetX, eyeOffsetY, eyeSize, 0, Math.PI * 2);
        lctx.fill();

        // 瞳孔随鼠标移动
        lctx.fillStyle = 'black';
        let pupilSize = eyeSize * 0.5;
        if (isError) pupilSize = eyeSize * 0.2;

        const px = Math.cos(lookAngle) * eyeSize * 0.4;
        const py = Math.sin(lookAngle) * eyeSize * 0.4;

        // 左瞳孔
        lctx.beginPath();
        lctx.arc(-eyeOffsetX + px, eyeOffsetY + py, pupilSize, 0, Math.PI * 2);
        lctx.fill();
        // 右瞳孔
        lctx.beginPath();
        lctx.arc(eyeOffsetX + px, eyeOffsetY + py, pupilSize, 0, Math.PI * 2);
        lctx.fill();

        // Mouth (正面嘴巴)
        lctx.strokeStyle = 'rgba(0,0,0,0.4)';
        lctx.lineWidth = 2;
        lctx.beginPath();
        if (isError) {
            lctx.moveTo(-6, 8);
            lctx.bezierCurveTo(-3, 4, 3, 12, 6, 8);
            lctx.stroke();
        } else {
            lctx.arc(0, 4, 6, 0.2, Math.PI - 0.2);
            lctx.stroke();
        }

        lctx.restore();
        requestAnimationFrame(animateLogo);
    }
    animateLogo();
}

init();
initLogo();
