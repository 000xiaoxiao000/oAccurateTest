const canvas = document.getElementById('login-canvas');
const ctx = canvas.getContext('2d');

let width, height;
let characters = [];
const characterCount = 12; // 减少数量，大尺寸更舒适
let mouseX = 0;
let mouseY = 0;
let isError = false;

// 扩展颜色方案 - 添加更多活泼的颜色
const colors = (window.OatPalette && window.OatPalette.primary) ? window.OatPalette.primary : [
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

const accessoryColor = '#6b7280';

function pickWeightedIndex(weights) {
    let total = 0;
    for (let i = 0; i < weights.length; i++) {
        total += weights[i];
    }
    let roll = Math.random() * total;
    for (let j = 0; j < weights.length; j++) {
        roll -= weights[j];
        if (roll <= 0) {
            return j;
        }
    }
    return Math.max(0, weights.length - 1);
}

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
        this.type = pickWeightedIndex([42, 29, 0, 29]);
        this.hatStyle = Math.floor(Math.random() * 3);
        this.glassesStyle = Math.floor(Math.random() * 3);
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

        // 眼睛参数（装饰物和眼睛共用，保证眼镜与眼睛对齐）
        const eyeOffsetX = this.radius * 0.35;
        const eyeOffsetY = -this.radius * 0.15;
        const eyeSize = this.radius * 0.25;

        // 装饰物（与身体一起旋转/移动）
        if (this.type === 1) { // 小帽子：在头顶
            drawHat(ctx, this.radius, accessoryColor, this.hatStyle);
        } else if (this.type === 3) { // 眼镜：盖住眼睛
            drawGlasses(ctx, eyeOffsetX, eyeOffsetY, this.radius, accessoryColor, this.glassesStyle);
        }

        // 眼睛（在同一变换下绘制，保证与身体与装饰物一致）
        ctx.fillStyle = 'white';

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

function drawGlasses(ctx, eyeOffsetX, eyeOffsetY, radius, color, styleIndex) {
    const style = styleIndex % 3;
    const lensRadius = Math.max(5, radius * 0.18);
    const frameWidth = Math.max(2, radius * 0.08);
    const bridgeWidth = Math.max(2, radius * 0.05);
    const armWidth = Math.max(2, radius * 0.045);

    ctx.save();
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = frameWidth;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    if (style === 0) {
        ctx.beginPath();
        ctx.arc(-eyeOffsetX, eyeOffsetY, lensRadius, 0, Math.PI * 2);
        ctx.arc(eyeOffsetX, eyeOffsetY, lensRadius, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(-eyeOffsetX + lensRadius, eyeOffsetY);
        ctx.lineTo(-bridgeWidth / 2, eyeOffsetY);
        ctx.moveTo(bridgeWidth / 2, eyeOffsetY);
        ctx.lineTo(eyeOffsetX - lensRadius, eyeOffsetY);
        ctx.stroke();
    } else if (style === 1) {
        const w = lensRadius * 1.25;
        const h = lensRadius * 1.08;
        ctx.strokeRect(-eyeOffsetX - w, eyeOffsetY - h, w * 2, h * 2);
        ctx.strokeRect(eyeOffsetX - w, eyeOffsetY - h, w * 2, h * 2);
        ctx.beginPath();
        ctx.moveTo(-eyeOffsetX + w, eyeOffsetY);
        ctx.lineTo(-bridgeWidth / 2, eyeOffsetY);
        ctx.moveTo(bridgeWidth / 2, eyeOffsetY);
        ctx.lineTo(eyeOffsetX - w, eyeOffsetY);
        ctx.stroke();
    } else {
        const w = lensRadius * 1.2;
        const h = lensRadius * 1.02;
        ctx.beginPath();
        ctx.moveTo(-eyeOffsetX - w, eyeOffsetY - h);
        ctx.lineTo(-eyeOffsetX + w, eyeOffsetY - h);
        ctx.lineTo(-eyeOffsetX + w, eyeOffsetY + h * 0.1);
        ctx.lineTo(-eyeOffsetX - w, eyeOffsetY + h * 0.1);
        ctx.closePath();
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(eyeOffsetX - w, eyeOffsetY - h);
        ctx.lineTo(eyeOffsetX + w, eyeOffsetY - h);
        ctx.lineTo(eyeOffsetX + w, eyeOffsetY + h * 0.1);
        ctx.lineTo(eyeOffsetX - w, eyeOffsetY + h * 0.1);
        ctx.closePath();
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(-eyeOffsetX + w, eyeOffsetY);
        ctx.lineTo(-bridgeWidth / 2, eyeOffsetY);
        ctx.moveTo(bridgeWidth / 2, eyeOffsetY);
        ctx.lineTo(eyeOffsetX - w, eyeOffsetY);
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(-eyeOffsetX - w, eyeOffsetY - h * 0.45);
        ctx.lineTo(-eyeOffsetX - w - armWidth * 4, eyeOffsetY - h * 0.2);
        ctx.moveTo(eyeOffsetX + w, eyeOffsetY - h * 0.45);
        ctx.lineTo(eyeOffsetX + w + armWidth * 4, eyeOffsetY - h * 0.2);
        ctx.stroke();
    }

    ctx.beginPath();
    ctx.moveTo(-eyeOffsetX - lensRadius * 0.8, eyeOffsetY - lensRadius * 0.6);
    ctx.lineTo(-eyeOffsetX - lensRadius * 1.5, eyeOffsetY - lensRadius * 0.25);
    ctx.moveTo(eyeOffsetX + lensRadius * 0.8, eyeOffsetY - lensRadius * 0.6);
    ctx.lineTo(eyeOffsetX + lensRadius * 1.5, eyeOffsetY - lensRadius * 0.25);
    ctx.stroke();
    ctx.restore();
}

function drawHat(ctx, radius, color, styleIndex) {
    const style = styleIndex % 3;
    const hatY = -radius * 1.05;

    ctx.save();
    ctx.fillStyle = color;
    ctx.strokeStyle = color;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    if (style === 0) {
        ctx.fillRect(-radius * 0.6, hatY - 4, radius * 1.2, 6);
        ctx.fillRect(-radius * 0.35, hatY - 16, radius * 0.7, 12);
    } else if (style === 1) {
        ctx.beginPath();
        ctx.moveTo(-radius * 0.55, hatY + 2);
        ctx.lineTo(0, hatY - 14);
        ctx.lineTo(radius * 0.55, hatY + 2);
        ctx.quadraticCurveTo(0, hatY + 10, -radius * 0.55, hatY + 2);
        ctx.fill();
        ctx.fillRect(-radius * 0.5, hatY + 2, radius * 1.0, 5);
    } else {
        ctx.beginPath();
        ctx.moveTo(-radius * 0.5, hatY + 5);
        ctx.quadraticCurveTo(0, hatY - 18, radius * 0.5, hatY + 5);
        ctx.quadraticCurveTo(0, hatY + 12, -radius * 0.5, hatY + 5);
        ctx.fill();
        ctx.fillRect(-radius * 0.52, hatY + 4, radius * 1.04, 4);
    }

    ctx.restore();
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
