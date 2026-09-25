#!/usr/bin/env bash
#
# Despliega RentEquip en una EC2 conectada a la RDS ya existente.
#
# Pensado para AWS Academy (Vocareum), cuyas credenciales son temporales y caducan
# en unas horas. Transfiere el JAR mediante una URL prefirmada de S3 en lugar de SSH,
# porque asi la instancia no necesita ni clave ni instance profile.
#
# USO:
#   1. Exporta las credenciales del panel "AWS Details > AWS CLI" de Vocareum.
#   2. export DB_PASSWORD='la-contrasena-de-tu-RDS'
#   3. bash deploy/deploy-aws.sh
#
set -euo pipefail

# --------------------------------------------------------------------- parametros
REGION="${AWS_REGION:-us-east-1}"
# t3.small (2 GB) y no t3.micro (1 GB): con 916 MB utiles, la JVM mas Hibernate
# arrancando el esquema deja demasiado poco margen.
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.small}"
APP_PORT=8080
TAG="rentequip"

DB_HOST="${DB_HOST:-rentequipdbp.crxqh2f7lhqi.us-east-1.rds.amazonaws.com}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-rentequip}"
DB_USER="${DB_USER:-postgres}"

JAR_LOCAL="target/backend-0.0.1-SNAPSHOT.jar"

say() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
die() { printf '\n\033[31mERROR: %s\033[0m\n' "$1" >&2; exit 1; }

# --------------------------------------------------------------------- 0. requisitos
say "Comprobando requisitos"
command -v aws >/dev/null 2>&1 || die "La AWS CLI no esta instalada. Instalala desde https://aws.amazon.com/cli/"
[ -f "$JAR_LOCAL" ] || die "No existe $JAR_LOCAL. Ejecuta antes: ./mvnw clean package -DskipTests"
[ -n "${DB_PASSWORD:-}" ] || die "Falta DB_PASSWORD. Ejecuta: export DB_PASSWORD='tu-password-de-RDS'"
[ -n "${JWT_SECRET:-}" ] || JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n=' | head -c 48)"

say "Verificando identidad en AWS"
aws sts get-caller-identity --output table || die "Credenciales invalidas o caducadas. Vuelve a copiarlas de Vocareum."

# --------------------------------------------------------------------- 1. subir el jar
BUCKET="rentequip-deploy-$(date +%s)"
say "Creando bucket temporal $BUCKET y subiendo el JAR (66 MB, puede tardar)"
aws s3 mb "s3://$BUCKET" --region "$REGION" >/dev/null
aws s3 cp "$JAR_LOCAL" "s3://$BUCKET/app.jar" --region "$REGION"
JAR_URL="$(aws s3 presign "s3://$BUCKET/app.jar" --expires-in 3600 --region "$REGION")"

# --------------------------------------------------------------------- 2. security group
say "Creando security group"
VPC_ID="$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true \
          --query 'Vpcs[0].VpcId' --output text --region "$REGION")"
SG_ID="$(aws ec2 create-security-group \
          --group-name "${TAG}-sg-$(date +%s)" \
          --description "RentEquip backend - puerto $APP_PORT publico" \
          --vpc-id "$VPC_ID" --query 'GroupId' --output text --region "$REGION")"

aws ec2 authorize-security-group-ingress --group-id "$SG_ID" \
  --protocol tcp --port "$APP_PORT" --cidr 0.0.0.0/0 --region "$REGION" >/dev/null
echo "Security group: $SG_ID (puerto $APP_PORT abierto)"

# La RDS no es publica. En lugar de abrirla a internet, se autoriza unicamente al
# security group del backend, que es el minimo privilegio que hace falta.
RDS_SG="${RDS_SG:-sg-0bf0c6c75c912183d}"
say "Autorizando el acceso del backend a la RDS ($RDS_SG)"
aws ec2 authorize-security-group-ingress --group-id "$RDS_SG"   --protocol tcp --port 5432 --source-group "$SG_ID" --region "$REGION" >/dev/null 2>&1   && echo "Regla anadida: 5432 desde $SG_ID"   || echo "La regla ya existia, se continua"

# --------------------------------------------------------------------- 3. lanzar la instancia
say "Buscando la AMI de Amazon Linux 2023"
# Se resuelve con describe-images y no con el parametro publico de SSM: en Git Bash sobre
# Windows, MSYS reescribe la ruta "/aws/service/..." como ruta de Windows y la consulta
# devuelve None, lo que hace fallar run-instances con InvalidAMIID.Malformed.
AMI_ID="$(aws ec2 describe-images --owners amazon \
  --filters "Name=name,Values=al2023-ami-2023*-x86_64" "Name=state,Values=available" \
  --query 'reverse(sort_by(Images,&CreationDate))[0].ImageId' \
  --output text --region "$REGION")"
[ -n "$AMI_ID" ] && [ "$AMI_ID" != "None" ] || die "No se pudo resolver la AMI"
echo "AMI: $AMI_ID"

JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

USER_DATA="$(cat <<EOF
#!/bin/bash
set -x
dnf install -y java-21-amazon-corretto-headless
mkdir -p /opt/rentequip
curl -sS -o /opt/rentequip/app.jar "$JAR_URL"

cat > /etc/systemd/system/rentequip.service <<'UNIT'
[Unit]
Description=RentEquip backend
After=network-online.target

[Service]
ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar /opt/rentequip/app.jar
Restart=always
RestartSec=10
# El log tambien va a la consola serie: es la unica forma de diagnosticar la app
# desde fuera, porque la instancia se lanza sin par de claves y no admite SSH.
StandardOutput=journal+console
StandardError=journal+console
Environment="DB_URL=$JDBC_URL"
Environment="DB_USERNAME=$DB_USER"
Environment="DB_PASSWORD=$DB_PASSWORD"
Environment="JWT_SECRET=$JWT_SECRET"
Environment="MAIL_ENABLED=false"
Environment="PORT=$APP_PORT"

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now rentequip
EOF
)"

say "Lanzando instancia $INSTANCE_TYPE"
INSTANCE_ID="$(aws ec2 run-instances \
  --image-id "$AMI_ID" --instance-type "$INSTANCE_TYPE" \
  --security-group-ids "$SG_ID" \
  --user-data "$USER_DATA" \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$TAG}]" \
  --query 'Instances[0].InstanceId' --output text --region "$REGION")"
echo "Instancia: $INSTANCE_ID"

aws ec2 wait instance-running --instance-ids "$INSTANCE_ID" --region "$REGION"
PUBLIC_IP="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text --region "$REGION")"
echo "IP publica: $PUBLIC_IP"

# --------------------------------------------------------------------- 4. esperar a la app
say "Esperando a que la API responda (arranque + descarga del JAR, ~3 minutos)"
HEALTH="http://${PUBLIC_IP}:${APP_PORT}/actuator/health"
for i in $(seq 1 40); do
  if curl -fsS --max-time 5 "$HEALTH" >/dev/null 2>&1; then
    echo
    curl -sS "$HEALTH"; echo
    say "LISTO: http://${PUBLIC_IP}:${APP_PORT}/swagger-ui.html"
    break
  fi
  printf '.'
  sleep 15
  [ "$i" -eq 40 ] && die "La API no respondio en 10 minutos. Revisa: aws ec2 get-console-output --instance-id $INSTANCE_ID --region $REGION"
done

# --------------------------------------------------------------------- 5. limpiar y documentar
aws s3 rm "s3://$BUCKET/app.jar" --region "$REGION" >/dev/null 2>&1 || true
aws s3 rb "s3://$BUCKET" --region "$REGION" >/dev/null 2>&1 || true

say "Actualizando el README"
BASE="http://${PUBLIC_IP}:${APP_PORT}"
sed -i "s|^\*\*Deployment:\*\* .*|**Deployment:** ${BASE}/swagger-ui.html|" README.md
grep -n "Deployment:" README.md

cat <<RESUMEN

  URL base      : $BASE
  Swagger       : $BASE/swagger-ui.html
  Health        : $BASE/actuator/health
  Instancia     : $INSTANCE_ID
  Security group: $SG_ID

  Siguiente paso:
    git add README.md && git commit -m "docs: publicar la URL del deployment" && git push

  Para apagarlo al terminar:
    aws ec2 terminate-instances --instance-ids $INSTANCE_ID --region $REGION

RESUMEN
